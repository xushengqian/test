package com.ivr.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IVR System Main Application
 * Interactive Voice Response System with FreeSWITCH Integration
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class IvrApplication {

    public static void main(String[] args) {
        SpringApplication.run(IvrApplication.class, args);
    }
}
