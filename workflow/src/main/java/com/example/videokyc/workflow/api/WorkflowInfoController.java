package com.example.videokyc.workflow.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow")
@Tag(name = "Workflow Lifecycle", description = "Scaffold state; engine is not implemented")
public class WorkflowInfoController {
    private static final Logger log = LoggerFactory.getLogger(WorkflowInfoController.class);
    @Operation(summary = "Describe workflow service readiness", description = "Reports NOT_IMPLEMENTED until a future phase")
    @ApiResponse(responseCode = "200", description = "Scaffold status")
    @GetMapping("/info")
    public WorkflowInfo info() {
        log.atInfo().addKeyValue("event", "workflow.action")
                .addKeyValue("action", "INFO").addKeyValue("outcome", "SUCCESS")
                .addKeyValue("engineStatus", "NOT_IMPLEMENTED").log("Workflow scaffold status requested");
        return new WorkflowInfo("WORKFLOW", "NOT_IMPLEMENTED", "State machine engine is planned for a later phase");
    }

    public record WorkflowInfo(String service, String status, String description) {}
}
