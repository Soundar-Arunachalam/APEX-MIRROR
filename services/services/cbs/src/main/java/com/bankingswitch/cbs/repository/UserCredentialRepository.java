package com.bankingswitch.cbs.repository;

import com.bankingswitch.cbs.model.entity.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, String> {
}
