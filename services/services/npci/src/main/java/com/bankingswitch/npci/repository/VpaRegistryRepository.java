package com.bankingswitch.npci.repository;

import com.bankingswitch.npci.model.entity.VpaRegistryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VpaRegistryRepository extends JpaRepository<VpaRegistryEntry, String> {
}
