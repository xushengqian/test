package com.example.metaspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MetaspaceDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MetaspaceDemoApplication.class, args);
    }
}