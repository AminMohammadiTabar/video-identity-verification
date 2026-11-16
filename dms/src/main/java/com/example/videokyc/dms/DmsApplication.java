package com.example.videokyc.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Standalone DMS service. Standalone file management service with REST API and disk storage. */
@SpringBootApplication
public class DmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(DmsApplication.class, args);
    }
}
