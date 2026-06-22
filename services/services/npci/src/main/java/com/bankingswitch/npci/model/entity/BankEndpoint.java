package com.bankingswitch.npci.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "bank_endpoints")
@Data
public class BankEndpoint {
    @Id
    private String bankCode;
    private String switchBaseUrl;
}
