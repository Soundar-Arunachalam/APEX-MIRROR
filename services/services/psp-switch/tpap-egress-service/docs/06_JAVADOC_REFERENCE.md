# Javadoc Reference: TPAP Egress Service

This document lists the core public interfaces, classes, and methods across the `tpap-egress-service` along with their Javadoc summaries and operational reasons.

## `com.pspswitch.tpapegress.dispatcher`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`WebhookDispatcherService`** | Orchestrates webhook dispatch: resolves handler via factory, delegates handle(). Act as the main entry controller in the dispatch chain. Does NOT catch or swallow exceptions — propagates upward to the Kafka consumer. | N/A | `dispatch(SwitchCompletedEvent)` - Dispatch a completed switch event to the appropriate handler.<br>`WebhookDispatcherService(EventHandlerFactory)` - Constructs the dispatcher service with the given handler factory. |
| **`EventHandlerFactory`** | Resolves the correct WebhookEventHandler for a given EventType. Adding a new event type = add one new @Component that implements the interface. | `ADR-001` (Polymorphic handler pattern — no if-else or switch dispatch) | `EventHandlerFactory(List<WebhookEventHandler>)` - Constructs the factory and registers all available handlers.<br>`getHandler(EventType)` - Returns the handler registered for this event type, or throws if none is registered (see ADR-001). |

## `com.pspswitch.tpapegress.dispatcher.handler`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`WebhookEventHandler`** | Polymorphic handler interface. One implementation per event type — no if-else in the dispatcher. Implementors outline the handler contract, governing missing config behaviors, logging, and retry logic. | `ADR-001`<br>`ADR-002`<br>`ADR-003` | `supportedType()` - Returns the target event type.<br>`handle(SwitchCompletedEvent)` - Process the event: config lookup → payload build → HTTP POST → delivery log. Returns silently without throwing if no active config is found — see ADR-003. Retries up to 3 times on 5xx or timeout. Does not retry on 4xx (see ADR-002). Must save the delivery attempt to the delivery log entity regardless of success. |
| **`PaymentPushHandler`** | Dispatches payload built for the payment push to the target TPAP URL via webhook client. | `ADR-002` (Retries up to 3 times on 5xx or timeout. Does not retry on 4xx) | `supportedType()` - Returns `PAYMENT_PUSH`.<br>`handle(SwitchCompletedEvent)` - Processes event explicitly obeying retry & skip semantics.<br>`PaymentPushHandler(...)` - Constructs the handler injecting required repositories and client. |
| **`BalanceInquiryHandler`** | Dispatches payload built for the balance inquiry sequence to the target TPAP URL via webhook client. | `ADR-002`, `ADR-003` (Returns silently without throwing if no active config is found) | `supportedType()` - Returns `BALANCE_INQUIRY`.<br>`handle(SwitchCompletedEvent)` - Processes event explicitly obeying retry & skip semantics.<br>`BalanceInquiryHandler(...)` - Constructs the handler injecting required repositories and client. |
| **`VpaVerificationHandler`** | Dispatches payload built for the VPA verification context to the target TPAP URL via webhook client. | `ADR-002`, `ADR-003` | `supportedType()` - Returns `VPA_VERIFICATION`.<br>`handle(SwitchCompletedEvent)` - Processes event explicitly obeying retry & skip semantics.<br>`VpaVerificationHandler(...)` - Constructs the handler injecting required repositories and client. |

## `com.pspswitch.tpapegress.client`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`WebhookHttpClient`** | Thin WebClient wrapper for outbound webhook HTTP POSTs. All retry orchestration lives in the handler. | `ADR-005` (No retry logic inside this class. Makes exactly ONE HTTP call with no internal retry and returns the status code.) | `post(String, WebhookPayload)` - POST the webhook payload to the given URL making exactly ONE HTTP call with no internal retry (ADR-005).<br>`WebhookHttpClient(...)` - Instantiates the client with specific web builder and timeout setups. |

## `com.pspswitch.tpapegress.model.entity`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`WebhookConfig`** | JPA entity for the webhook_configs table. Maps each (tpapId, eventType) to a webhook URL. | `ADR-003` | `active` field determines if URL delivery is active. If false, delivery is skipped and returns silently without throwing (see ADR-003). |
| **`DeliveryLog`** | JPA entity for the delivery_logs table. One row per dispatched event capturing the final delivery outcome. | N/A | `status` represents the exact status result of delivery process. Allowed values: SUCCESS, FAILED, SKIPPED. |

## `com.pspswitch.tpapegress.model.event`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`SwitchCompletedEvent`** | Kafka message envelope for completed PSP Switch operations. Structure determines polymorphic routing for handlers to perform HTTP calls. | N/A | standard getter/setters |
| **`EventType`** | Represents the categories of switch events flowing through the egress service. | N/A | Contains enums: PAYMENT_PUSH, BALANCE_INQUIRY, VPA_VERIFICATION |
| **Event implementations** | `PaymentPushEvent` / `BalanceInquiryEvent` / `VpaVerificationEvent` | N/A | Standard payload carrier subclasses. |

## `com.pspswitch.tpapegress.model.payload`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`WebhookPayload`** | Common webhook payload envelope. All concrete payloads implement this interface so the HTTP client can accept any payload type polymorphically for outbound HTTP posts. Provides guaranteed extraction of envelope-level fields representing the event. | N/A | `getEventId()`, `getEventType()`, `getTpapId()`, `getTxnId()`, `getCorrelationId()`, `getDeliveredAt()` |
| **Payload implementations** | `PaymentPushWebhookPayload` / `BalanceInquiryWebhookPayload` / `VpaVerificationWebhookPayload` | N/A | Data classes wrapping the envelope implementation alongside explicit transaction payloads. |

## `com.pspswitch.tpapegress.exception`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`UnknownEventTypeException`** | Thrown when the EventHandlerFactory cannot resolve a handler for the given event type (including null). | `ADR-001` (where unknown types lead to processing exception) | `UnknownEventTypeException(Object)` - Constructs exception outlining what event type had no concrete dispatch handler registered. |
| **`WebhookDeliveryException`** | Thrown by WebhookHttpClient on network-level failures: connection refused, read timeout, DNS resolution failure, etc. | N/A | `WebhookDeliveryException(String)`, `WebhookDeliveryException(String, Throwable)` |

## `com.pspswitch.tpapegress.repository`

| Class / Interface | Summary | ADR Reference | Method Highlights |
| :--- | :--- | :--- | :--- |
| **`DeliveryLogRepository`** | Repository interface for managing persistence operations on DeliveryLog entities. Handles exact logging traces during each Webhook execution trial. | N/A | Standard JPA repository methods. |
| **`WebhookConfigRepository`** | Repository interfacing persistence store containing target TPAP webhook configurations. Used internally for pathfinding registered target endpoints. | N/A | `findByTpapIdAndEventType(...)`<br>`findActiveConfig(...)` - Look up the webhook configuration for a given TPAP and event type. |
