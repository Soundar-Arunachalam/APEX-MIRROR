package com.bankingswitch.orchestrator.service;

import com.bankingswitch.orchestrator.model.TransactionState;
import com.bankingswitch.orchestrator.model.entity.EventLogEntity;
import com.bankingswitch.orchestrator.model.entity.TransactionEntity;
import com.bankingswitch.orchestrator.repository.EventLogRepository;
import com.bankingswitch.orchestrator.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStateService {

    private final TransactionRepository transactionRepository;
    private final EventLogRepository eventLogRepository;

    @Transactional
    public TransactionEntity createTransaction(String txnId, String txnType, String xmlPayload) {
        TransactionEntity entity = TransactionEntity.builder()
                .txnId(txnId)
                .txnType(txnType)
                .xmlPayload(xmlPayload)
                .state(TransactionState.RECEIVED)
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        
        transactionRepository.save(entity);
        logEvent(txnId, "CREATED", "Transaction initialized");
        return entity;
    }

    @Transactional
    public void updateTransactionState(String txnId, TransactionState state, String eventData) {
        transactionRepository.findById(txnId).ifPresent(entity -> {
            entity.setState(state);
            entity.setUpdatedAt(System.currentTimeMillis());
            transactionRepository.save(entity);
            logEvent(txnId, state.name(), eventData);
        });
    }

    public TransactionEntity getTransaction(String txnId) {
        return transactionRepository.findById(txnId).orElse(null);
    }

    private void logEvent(String txnId, String eventType, String eventData) {
        EventLogEntity logEntity = EventLogEntity.builder()
                .txnId(txnId)
                .eventType(eventType)
                .eventData(eventData)
                .timestamp(System.currentTimeMillis())
                .build();
        eventLogRepository.save(logEntity);
    }
}
