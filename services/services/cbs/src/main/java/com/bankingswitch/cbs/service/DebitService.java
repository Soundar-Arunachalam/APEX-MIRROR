package com.bankingswitch.cbs.service;

import com.bankingswitch.cbs.model.dto.DebitRequest;
import com.bankingswitch.cbs.model.dto.OperationResponse;
import com.bankingswitch.cbs.model.entity.Account;
import com.bankingswitch.cbs.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DebitService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public DebitService(AccountRepository accountRepository, LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OperationResponse debit(DebitRequest request) {
        Account account = accountRepository.findByVpaForUpdate(request.getVpa())
                .orElseThrow(() -> new RuntimeException("Account not found for VPA: " + request.getVpa()));

        BigDecimal balanceBefore = account.getBalance();

        if (balanceBefore.compareTo(request.getAmount()) < 0) {
            return new OperationResponse("INSUFFICIENT_FUNDS", balanceBefore, balanceBefore, "Not enough balance");
        }

        BigDecimal balanceAfter = balanceBefore.subtract(request.getAmount());
        account.setBalance(balanceAfter);
        accountRepository.save(account);

        ledgerService.recordTransaction(request.getTxnId(), request.getVpa(), "DEBIT", request.getAmount(), balanceBefore, balanceAfter);

        return new OperationResponse("SUCCESS", balanceBefore, balanceAfter, "Debit successful");
    }
}
