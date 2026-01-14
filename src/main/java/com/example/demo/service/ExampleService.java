package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExampleService {

    /**
     * 此方法以非事务方式运行。
     * 如果存在现有事务，则在执行此方法期间挂起该事务。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void nonTransactionalOperation() {
        System.out.println("Executing non-transactional operation...");
        // 这里执行不需要事务的操作，或者不想回滚的操作
        performHeavyCalculation();
    }

    @Transactional
    public void transactionalOperation() {
        System.out.println("Starting transactional operation...");
        // 此调用将在非事务上下文中运行，挂起当前事务
        nonTransactionalOperation();
        System.out.println("Finished transactional operation...");
    }

    private void performHeavyCalculation() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
