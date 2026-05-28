package com.myapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InvoiceProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceProcessorApplication.class, args);
    }
}
