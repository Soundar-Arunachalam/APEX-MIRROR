package com.bankingswitch.npci.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "vpa_registry")
@Data
public class VpaRegistryEntry {
    @Id
    private String vpa;
    private String bankCode;
    private String customerName;
}
