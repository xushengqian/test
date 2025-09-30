package com.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用程序主类
 */
@SpringBootApplication
@MapperScan("com.example.mapper")
public class Application {
    
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("\n========================================");
        System.out.println("应用启动成功！");
        System.out.println("H2控制台: http://localhost:8080/h2-console");
        System.out.println("========================================\n");
    }
}