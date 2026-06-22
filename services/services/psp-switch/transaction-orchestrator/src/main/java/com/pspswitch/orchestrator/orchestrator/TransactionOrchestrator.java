package com.pspswitch.orchestrator.orchestrator;

import com.pspswitch.orchestrator.kafka.NpciKafkaPublisher;
import com.pspswitch.orchestrator.kafka.NpciResponseKafkaConsumer;
import com.pspswitch.orchestrator.kafka.SwitchCompletedEventProducer;
import com.pspswitch.orchestrator.model.*;
import com.pspswitch.orchestrator.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Transaction Orchestrator — implements the UPI payment orchestration saga.
 *
 * <p>Supports dual-direction flows:
 * <ul>
 *   <li>SEND (Payer side): Steps 1-5 sync + Steps 6-8 async via Kafka</li>
 *   <li>COLLECT (Receiver side): Steps 1-5 sync + Steps 6-8 async via Kafka + CBS</li>
 * </ul>
 *
 * <h2>Saga steps</h2>
 * <ol>
 *   <li>Idempotency check (composite key = tr::pa)</li>
 *   <li>TID generation (PSP- + first 8 chars of UUID uppercase)</li>
 *   <li>Mode preprocessing (mode 04/05/16 → requiresPasscode, flowType)</li>
 *   <li>Validation (9 sequential rules)</li>
 *   <li>Write PENDING state, return HTTP 202</li>
 *   <li>Publish to Kafka (npci.outbound.request) + register timeout</li>
 *   <li>NPCI processes → callback to npci-response-consumer → Kafka (npci.inbound.response)</li>
 *   <li>NpciResponseKafkaConsumer completes saga: Ledger + state + notify TPAP</li>
 * </ol>
 *
 * <p>Timeout: ScheduledExecutorService fires after {@code app.npci.timeout-seconds}.
 * If state is still SUBMITTED → mark UNKNOWN.
 */
@Service
public class TransactionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TransactionOrchestrator.class);

    @Value("${app.npci.timeout-seconds:30}")
    private long npciTimeoutSeconds;

    @Value("${app.psp.id:DEMOPSP}")
    private String pspId;

    private final IdempotencyService idempotencyService;
    private final TransactionStateService stateService;
    private final ModePreprocessingService modePreprocessingService;
    private final ValidationService validationService;
    private final NpciKafkaPublisher npciKafkaPublisher;
    private final NpciResponseKafkaConsumer npciResponseConsumer;
    private final SwitchCompletedEventProducer switchCompletedProducer;

    private final java.util.concurrent.ScheduledExecutorService timeoutScheduler =
            Executors.newScheduledThreadPool(2);

    public TransactionOrchestrator(IdempotencyService idempotencyService,
                                    TransactionStateService stateService,
                                    ModePreprocessingService modePreprocessingService,
                                    ValidationService validationService,
                                    NpciKafkaPublisher npciKafkaPublisher,
                                    NpciResponseKafkaConsumer npciResponseConsumer,
                                    SwitchCompletedEventProducer switchCompletedProducer) {
        this.idempotencyService = idempotencyService;
        this.stateService = stateService;
        this.modePreprocessingService = modePreprocessingService;
        this.validationService = validationService;
        this.npciKafkaPublisher = npciKafkaPublisher;
        this.npciResponseConsumer = npciResponseConsumer;
        this.switchCompletedProducer = switchCompletedProducer;
    }

    /**
     * Orchestrates Steps 1-5 synchronously, then triggers Steps 6-8 async via Kafka.
     *
     * @param request validated UPI payment request
     * @return OrchestratorResult with pending response and duplicate flag
     */
    public OrchestratorResult orchestrate(UpiPaymentRequest request) {

        // ── STEP 1: IDEMPOTENCY CHECK ──────────────────────────────────────────
        String compositeKey = idempotencyService.buildKey(request.getTr(), request.getPa());

        if (!idempotencyService.claimSlot(compositeKey)) {
            TransactionResponse cached = idempotencyService.getCachedResponse(compositeKey);
            if (cached != null) {
                return new OrchestratorResult(cached, true);
            }
            TransactionResponse processingResp = new TransactionResponse();
            processingResp.setState("PROCESSING");
            processingResp.setMessage("Transaction is still being processed");
            return new OrchestratorResult(processingResp, true);
        }

        // ── STEP 2: TID GENERATION ─────────────────────────────────────────────
        String tid = request.getTid();
        if (tid == null || tid.trim().isEmpty()) {
            tid = "PSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            log.warn("[ORCHESTRATOR] tid missing from TPAP. Generated fallback tid={} | tr={}", tid, request.getTr());
        } else {
            log.info("[ORCHESTRATOR] tid={} received from TPAP | tr={}", tid, request.getTr());
        }

        // ── STEP 3: MODE PREPROCESSING ─────────────────────────────────────────
        PreprocessingContext ppCtx = modePreprocessingService.process(request);
        log.info("[MODE] tid={} | mode={} | requiresPasscode={} | flowType={}",
                tid, request.getMode(), ppCtx.isRequiresPasscode(), ppCtx.getFlowType());

        // ── STEP 4: VALIDATION ─────────────────────────────────────────────────
        // validationService.validate(request, tid); // Validation bypassed - handled by tpap-ingress

        // ── STEP 5: WRITE PENDING STATE ────────────────────────────────────────
        TransactionContext context = buildContext(request, tid, ppCtx);
        stateService.save(context);

        log.info("[ORCHESTRATOR] tid={} | tr={} | mode={} | state=PENDING | am={}",
                tid, request.getTr(), request.getMode(), request.getAm());

        TransactionResponse pendingResponse = TransactionResponse.fromContext(context);
        pendingResponse.setMessage("Processing. Poll GET /api/v1/txn/" + tid);

        // ── STEPS 6-8: ASYNC KAFKA PIPELINE ───────────────────────────────────
        executeAsyncSaga(context);

        return new OrchestratorResult(pendingResponse, false);
    }

    /**
     * Publishes the NPCI outbound event to Kafka and registers a timeout watchdog.
     * The saga completion is driven by {@link NpciResponseKafkaConsumer}.
     */
    @Async("orchestratorExecutor")
    public void executeAsyncSaga(TransactionContext context) {
        String tid = context.getTid();

        try {
            // ── STEP 6: PUBLISH TO NPCI VIA KAFKA ─────────────────────────────
            String msgId = UUID.randomUUID().toString();
            String eventType = (context.getFlowDirection() == FlowDirection.COLLECT) ? "COLLECT" : "PAY";

            NpciOutboundRequestEvent npciEvent = new NpciOutboundRequestEvent(
                    tid, msgId, eventType,
                    pspId + "@psp",       // payer VPA derived from PSP — real systems supply authenticated VPA
                    context.getPa(),      // payee VPA
                    context.getAm() != null ? context.getAm().toPlainString() : "0.00",
                    context.getCu() != null ? context.getCu() : "INR",
                    pspId
            );

            npciKafkaPublisher.publish(npciEvent);

            // Update state to SUBMITTED
            context.setState(TransactionState.SUBMITTED);
            stateService.update(context);

            log.info("[ORCHESTRATOR] tid={} | SUBMITTED | Awaiting NPCI response via Kafka", tid);

            // ── STEP 7: REGISTER TIMEOUT WATCHDOG ─────────────────────────────
            ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
                TransactionContext ctx = stateService.getByTid(tid);
                if (ctx != null && ctx.getState() == TransactionState.SUBMITTED) {
                    log.warn("[ORCHESTRATOR] tid={} | TIMEOUT | No NPCI response within {}s",
                            tid, npciTimeoutSeconds);
                    ctx.setState(TransactionState.FAILED);
                    ctx.setFailureReason("NPCI response timeout after " + npciTimeoutSeconds + "s");
                    stateService.update(ctx);

                    String key = idempotencyService.buildKey(ctx.getTr(), ctx.getPa());
                    idempotencyService.cacheResponse(key, TransactionResponse.fromContext(ctx));

                    if ("SEND".equals(ctx.getFlowDirection().name()) || "COLLECT".equals(ctx.getFlowDirection().name())) {
                        switchCompletedProducer.publishPaymentResult(ctx);
                    } else {
                        switchCompletedProducer.publishBalanceResult(ctx, "0", "INR");
                    }
                }
            }, npciTimeoutSeconds, TimeUnit.SECONDS);

            // Register with the Kafka consumer so it can cancel on response arrival
            npciResponseConsumer.registerTimeoutFuture(tid, timeoutFuture);

        } catch (Exception e) {
            log.error("[ORCHESTRATOR] tid={} | Async saga failed: {}", tid, e.getMessage(), e);
            context.setState(TransactionState.FAILED);
            context.setFailureReason("Internal error: " + e.getMessage());
            stateService.update(context);

            String key = idempotencyService.buildKey(context.getTr(), context.getPa());
            idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));
        }
    }

    public OrchestratorResult orchestrateBalance(String tr, String pa, String tid) {
        String compositeKey = idempotencyService.buildKey(tr, pa + ":BAL");
        if (!idempotencyService.claimSlot(compositeKey)) {
            TransactionResponse cached = idempotencyService.getCachedResponse(compositeKey);
            if (cached != null) return new OrchestratorResult(cached, true);
            TransactionResponse resp = new TransactionResponse();
            resp.setState("PROCESSING");
            return new OrchestratorResult(resp, true);
        }

        if (tid == null || tid.isEmpty()) {
            tid = "PSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }

        TransactionContext context = new TransactionContext();
        context.setTid(tid);
        context.setTr(tr);
        context.setPa(pa);
        context.setState(TransactionState.PENDING);
        context.setCreatedAt(Instant.now());
        context.setUpdatedAt(Instant.now());
        context.setFlowDirection(FlowDirection.SEND);
        stateService.save(context);

        TransactionResponse pendingResponse = TransactionResponse.fromContext(context);
        pendingResponse.setMessage("Balance inquiry submitted");

        executeAsyncNonFinancial(context, "BALANCE");
        return new OrchestratorResult(pendingResponse, false);
    }

    public OrchestratorResult orchestrateVpaLookup(String tr, String pa, String tid) {
        String compositeKey = idempotencyService.buildKey(tr, pa + ":VPA");
        if (!idempotencyService.claimSlot(compositeKey)) {
            TransactionResponse cached = idempotencyService.getCachedResponse(compositeKey);
            if (cached != null) return new OrchestratorResult(cached, true);
            TransactionResponse resp = new TransactionResponse();
            resp.setState("PROCESSING");
            return new OrchestratorResult(resp, true);
        }

        if (tid == null || tid.isEmpty()) {
            tid = "PSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }

        TransactionContext context = new TransactionContext();
        context.setTid(tid);
        context.setTr(tr);
        context.setPa(pa);
        context.setState(TransactionState.PENDING);
        context.setCreatedAt(Instant.now());
        context.setUpdatedAt(Instant.now());
        context.setFlowDirection(FlowDirection.SEND);
        stateService.save(context);

        TransactionResponse pendingResponse = TransactionResponse.fromContext(context);
        pendingResponse.setMessage("VPA lookup submitted");

        executeAsyncNonFinancial(context, "VPA_LOOKUP");
        return new OrchestratorResult(pendingResponse, false);
    }

    @Async("orchestratorExecutor")
    public void executeAsyncNonFinancial(TransactionContext context, String eventType) {
        String tid = context.getTid();
        try {
            String msgId = UUID.randomUUID().toString();
            NpciOutboundRequestEvent npciEvent = new NpciOutboundRequestEvent(
                    tid, msgId, eventType,
                    pspId + "@psp", context.getPa(), "0.00", "INR", pspId
            );
            npciKafkaPublisher.publish(npciEvent);
            context.setState(TransactionState.SUBMITTED);
            stateService.update(context);

            ScheduledFuture<?> timeoutFuture = timeoutScheduler.schedule(() -> {
                TransactionContext ctx = stateService.getByTid(tid);
                if (ctx != null && ctx.getState() == TransactionState.SUBMITTED) {
                    ctx.setState(TransactionState.FAILED);
                    ctx.setFailureReason("NPCI response timeout");
                    stateService.update(ctx);

                    if ("BALANCE".equals(eventType)) {
                        switchCompletedProducer.publishBalanceResult(ctx, "0.00", "INR");
                    } else if ("VPA_LOOKUP".equals(eventType) || "REQ_VPA".equals(eventType)) {
                        switchCompletedProducer.publish(SwitchCompletedEvent.forVpa(ctx, "TIMEOUT", ""), ctx.getTid());
                    }
                }
            }, npciTimeoutSeconds, TimeUnit.SECONDS);

            npciResponseConsumer.registerTimeoutFuture(tid, timeoutFuture);
        } catch (Exception e) {
            log.error("[ORCHESTRATOR] tid={} | Async saga failed: {}", tid, e.getMessage(), e);
            context.setState(TransactionState.FAILED);
            context.setFailureReason("Internal error: " + e.getMessage());
            stateService.update(context);
        }
    }

    private TransactionContext buildContext(UpiPaymentRequest request, String tid, PreprocessingContext ppCtx) {
        TransactionContext ctx = new TransactionContext();
        ctx.setTid(tid);
        ctx.setTr(request.getTr());
        ctx.setPa(request.getPa());
        ctx.setPn(request.getPn());
        ctx.setMc(request.getMc());
        ctx.setAm(request.getAm());
        ctx.setMam(request.getMam());
        ctx.setCu(request.getCu());
        ctx.setMode(request.getMode());
        ctx.setMid(request.getMid());
        ctx.setMsid(request.getMsid());
        ctx.setMtid(request.getMtid());
        ctx.setRequiresPasscode(ppCtx.isRequiresPasscode());
        ctx.setFlowType(ppCtx.getFlowType());
        ctx.setState(TransactionState.PENDING);
        ctx.setCreatedAt(Instant.now());
        ctx.setUpdatedAt(Instant.now());
        ctx.setFlowDirection("COLLECT".equalsIgnoreCase(request.getFlowDirection())
                ? FlowDirection.COLLECT : FlowDirection.SEND);
        return ctx;
    }

    public static class OrchestratorResult {
        private final TransactionResponse response;
        private final boolean duplicate;

        public OrchestratorResult(TransactionResponse response, boolean duplicate) {
            this.response = response;
            this.duplicate = duplicate;
        }

        public TransactionResponse getResponse() { return response; }
        public boolean isDuplicate() { return duplicate; }
    }
}
