package com.example.transaction.repository;

import com.example.transaction.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

/**
 * 用户账户仓储
 */
@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    
    UserAccount findByUsername(String username);
    
    @Modifying
    @Query("UPDATE UserAccount u SET u.balance = u.balance + :amount WHERE u.id = :id")
    int updateBalance(@Param("id") Long id, @Param("amount") BigDecimal amount);
}