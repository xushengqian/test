package com.example.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Spring 事务失效场景演示应用
 * 
 * @author example
 */
@SpringBootApplication
@EnableTransactionManagement
public class TransactionFailureApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionFailureApplication.class, args);
    }
}