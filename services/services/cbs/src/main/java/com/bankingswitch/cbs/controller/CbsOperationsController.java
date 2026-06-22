package com.bankingswitch.cbs.controller;

import com.bankingswitch.cbs.model.dto.BalanceResponse;
import com.bankingswitch.cbs.model.dto.CreditRequest;
import com.bankingswitch.cbs.model.dto.DebitRequest;
import com.bankingswitch.cbs.model.dto.OperationResponse;
import com.bankingswitch.cbs.service.AccountService;
import com.bankingswitch.cbs.service.CreditService;
import com.bankingswitch.cbs.service.DebitService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cbs")
public class CbsOperationsController {

    private final AccountService accountService;
    private final DebitService debitService;
    private final CreditService creditService;

    public CbsOperationsController(AccountService accountService, DebitService debitService, CreditService creditService) {
        this.accountService = accountService;
        this.debitService = debitService;
        this.creditService = creditService;
    }

    @GetMapping("/balance/{vpa}")
    public BalanceResponse getBalance(@PathVariable String vpa) {
        return accountService.getBalance(vpa);
    }

    @PostMapping("/debit")
    public OperationResponse debit(@RequestBody DebitRequest request) {
        try {
            return debitService.debit(request);
        } catch (Exception e) {
            return new OperationResponse("FAILED", null, null, e.getMessage());
        }
    }

    @PostMapping("/credit")
    public OperationResponse credit(@RequestBody CreditRequest request) {
        try {
            return creditService.credit(request);
        } catch (Exception e) {
            return new OperationResponse("FAILED", null, null, e.getMessage());
        }
    }
}
