package com.bankingswitch.npci.service;

import com.bankingswitch.npci.model.dto.UpiRequest;
import com.bankingswitch.npci.model.dto.UpiResponse;
import com.bankingswitch.npci.model.entity.VpaRegistryEntry;
import com.bankingswitch.npci.repository.VpaRegistryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class VpaDiscoveryService {

    private final VpaRegistryRepository registryRepository;

    public VpaDiscoveryService(VpaRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    public UpiResponse processValAdd(UpiRequest request) {
        String payeeVpa = request.getPayee().getVpa();
        Optional<VpaRegistryEntry> entry = registryRepository.findById(payeeVpa);

        UpiResponse response = new UpiResponse();
        UpiResponse.Txn txn = new UpiResponse.Txn();
        txn.setId(request.getTxn().getId());
        txn.setType("RespValAdd");
        response.setTxn(txn);

        UpiResponse.Resp resp = new UpiResponse.Resp();
        if (entry.isPresent()) {
            resp.setResult("SUCCESS");
            resp.setCustomerName(entry.get().getCustomerName());
        } else {
            resp.setResult("FAILURE");
            resp.setErrCode("INVALID_VPA");
        }
        response.setResp(resp);

        return response;
    }
}
