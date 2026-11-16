package com.example.videokyc.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Standalone WORKFLOW service. Audit persistence is available; no workflow engine is implemented. */
@SpringBootApplication
public class WorkflowApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
