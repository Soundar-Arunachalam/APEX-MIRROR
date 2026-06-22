package com.bankingswitch.npci.repository;

import com.bankingswitch.npci.model.entity.BankEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankEndpointRepository extends JpaRepository<BankEndpoint, String> {
}
