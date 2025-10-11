package com.example.transaction.repository;

import com.example.transaction.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 交易记录仓储
 */
@Repository
public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
}