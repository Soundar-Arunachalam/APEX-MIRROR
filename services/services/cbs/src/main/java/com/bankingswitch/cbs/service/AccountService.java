package com.bankingswitch.cbs.service;

import com.bankingswitch.cbs.model.dto.BalanceResponse;
import com.bankingswitch.cbs.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public BalanceResponse getBalance(String vpa) {
        return accountRepository.findById(vpa)
                .map(account -> new BalanceResponse(account.getVpa(), account.getBalance(), "SUCCESS"))
                .orElse(new BalanceResponse(vpa, null, "NOT_FOUND"));
    }
}
