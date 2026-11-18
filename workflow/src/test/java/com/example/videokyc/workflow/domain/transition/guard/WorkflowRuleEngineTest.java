package com.example.videokyc.workflow.domain.transition.guard;

import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRuleEngineTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ContextSchema sampleSchema = ContextSchema.of("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "user": {
                  "type": "object",
                  "properties": {
                    "age": { "type": "integer", "minimum": 18 },
                    "verified": { "type": "boolean" }
                  },
                  "required": ["age", "verified"],
                  "additionalProperties": false
                }
              },
              "required": ["user"],
              "additionalProperties": false
            }
            """);

    @Test
    @DisplayName("Should unconditionally match when Guard is null")
    void shouldMatchWhenGuardIsNull() throws Exception {
        JsonNode context = MAPPER.readTree("{\"user\": {\"age\": 20, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(null, context, sampleSchema);

        assertThat(result.matched()).isTrue();
        assertThat(result.error()).isEqualTo(GuardEvaluationError.NONE);
    }

    @Test
    @DisplayName("Should return MALFORMED_CONTEXT when context is null or non-object")
    void shouldReturnMalformedContext() {
        GuardExpression guard = GuardExpression.of("{\"operator\": \"EQ\", \"path\": \"user.verified\", \"value\": true}");

        GuardEvaluationResult nullRes = WorkflowRuleEngine.evaluate(guard, null);
        assertThat(nullRes.matched()).isFalse();
        assertThat(nullRes.error()).isEqualTo(GuardEvaluationError.MALFORMED_CONTEXT);

        JsonNode arrayNode = MAPPER.createArrayNode();
        GuardEvaluationResult arrayRes = WorkflowRuleEngine.evaluate(guard, arrayNode);
        assertThat(arrayRes.matched()).isFalse();
        assertThat(arrayRes.error()).isEqualTo(GuardEvaluationError.MALFORMED_CONTEXT);
    }

    @Test
    @DisplayName("Should return SCHEMA_VIOLATION when context does not satisfy ContextSchema")
    void shouldReturnSchemaViolation() throws Exception {
        GuardExpression guard = GuardExpression.of("{\"operator\": \"EQ\", \"path\": \"user.verified\", \"value\": true}");
        // Missing required 'verified' field
        JsonNode invalidContext = MAPPER.readTree("{\"user\": {\"age\": 25}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, invalidContext, sampleSchema);

        assertThat(result.matched()).isFalse();
        assertThat(result.error()).isEqualTo(GuardEvaluationError.SCHEMA_VIOLATION);
        assertThat(result.reason()).contains("Context schema validation failed");
    }

    @Test
    @DisplayName("Should match when all conditions in ALL node pass")
    void shouldMatchLogicalAll() throws Exception {
        GuardExpression guard = GuardExpression.of("""
                {
                  "type": "ALL",
                  "conditions": [
                    { "operator": "GTE", "path": "user.age", "value": 18 },
                    { "operator": "EQ", "path": "user.verified", "value": true }
                  ]
                }
                """);
        JsonNode validContext = MAPPER.readTree("{\"user\": {\"age\": 25, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, validContext, sampleSchema);

        assertThat(result.matched()).isTrue();
        assertThat(result.evaluatedPaths()).containsExactly("user.age", "user.verified");
    }

    @Test
    @DisplayName("Should report specific failing leaf when ALL node fails")
    void shouldReportFailingLeafInLogicalAll() throws Exception {
        GuardExpression guard = GuardExpression.of("""
                {
                  "type": "ALL",
                  "conditions": [
                    { "operator": "GTE", "path": "user.age", "value": 30 },
                    { "operator": "EQ", "path": "user.verified", "value": true }
                  ]
                }
                """);
        JsonNode context = MAPPER.readTree("{\"user\": {\"age\": 20, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, context, sampleSchema);

        assertThat(result.matched()).isFalse();
        assertThat(result.reason()).contains("ALL condition failed");
        assertThat(result.reason()).contains("user.age");
        assertThat(result.error()).isEqualTo(GuardEvaluationError.NONE);
    }

    @Test
    @DisplayName("Should match ANY node when at least one branch matches")
    void shouldMatchLogicalAny() throws Exception {
        GuardExpression guard = GuardExpression.of("""
                {
                  "type": "ANY",
                  "conditions": [
                    { "operator": "GTE", "path": "user.age", "value": 100 },
                    { "operator": "EQ", "path": "user.verified", "value": true }
                  ]
                }
                """);
        JsonNode context = MAPPER.readTree("{\"user\": {\"age\": 20, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, context, sampleSchema);

        assertThat(result.matched()).isTrue();
    }

    @Test
    @DisplayName("Should invert condition with NOT node")
    void shouldEvaluateLogicalNot() throws Exception {
        GuardExpression guard = GuardExpression.of("""
                {
                  "type": "NOT",
                  "condition": { "operator": "EQ", "path": "user.verified", "value": false }
                }
                """);
        JsonNode context = MAPPER.readTree("{\"user\": {\"age\": 20, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, context, sampleSchema);

        assertThat(result.matched()).isTrue();
    }

    @Test
    @DisplayName("Should return TYPE_MISMATCH when comparison operator encounters invalid property type")
    void shouldReturnTypeMismatchDiagnosticError() throws Exception {
        // GT operator applied to boolean property
        GuardExpression guard = GuardExpression.of("""
                { "operator": "GT", "path": "user.verified", "value": 10 }
                """);
        JsonNode context = MAPPER.readTree("{\"user\": {\"age\": 20, \"verified\": true}}");

        GuardEvaluationResult result = WorkflowRuleEngine.evaluate(guard, context);

        assertThat(result.matched()).isFalse();
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(GuardEvaluationError.TYPE_MISMATCH);
        assertThat(result.evaluatedPaths()).contains("user.verified");
    }
}
