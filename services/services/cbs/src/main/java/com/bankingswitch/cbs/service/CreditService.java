package com.bankingswitch.cbs.service;

import com.bankingswitch.cbs.model.dto.CreditRequest;
import com.bankingswitch.cbs.model.dto.OperationResponse;
import com.bankingswitch.cbs.model.entity.Account;
import com.bankingswitch.cbs.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class CreditService {

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;

    public CreditService(AccountRepository accountRepository, LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public OperationResponse credit(CreditRequest request) {
        Account account = accountRepository.findByVpaForUpdate(request.getVpa())
                .orElseThrow(() -> new RuntimeException("Account not found for VPA: " + request.getVpa()));

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

        account.setBalance(balanceAfter);
        accountRepository.save(account);

        ledgerService.recordTransaction(request.getTxnId(), request.getVpa(), "CREDIT", request.getAmount(), balanceBefore, balanceAfter);

        return new OperationResponse("SUCCESS", balanceBefore, balanceAfter, "Credit successful");
    }
}
