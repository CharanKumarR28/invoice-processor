package com.myapp.repository;

import com.myapp.model.FileTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileTransactionRepository extends JpaRepository<FileTransaction, Long> {
    Optional<FileTransaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
}
