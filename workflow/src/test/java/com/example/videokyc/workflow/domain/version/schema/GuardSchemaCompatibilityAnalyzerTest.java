package com.example.videokyc.workflow.domain.version.schema;

import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuardSchemaCompatibilityAnalyzerTest {

    private final ContextSchema strictSchema = ContextSchema.of("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "applicant": {
                  "type": "object",
                  "properties": {
                    "age": { "type": "integer" },
                    "name": { "type": "string" },
                    "verified": { "type": "boolean" },
                    "status": { "type": "string", "enum": ["PENDING", "APPROVED"] }
                  },
                  "required": ["age", "name"],
                  "additionalProperties": false
                },
                "score": { "type": "number" },
                "metadata": {
                  "type": "object",
                  "additionalProperties": true
                }
              },
              "additionalProperties": false
            }
            """);

    @Test
    @DisplayName("Should return compatible when guard is null")
    void shouldReportCompatibleWhenGuardIsNull() {
        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(null, strictSchema);
        assertThat(report.compatible()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    @DisplayName("Should report compatible for matching nested types")
    void shouldReportCompatibleForMatchingNestedTypes() {
        GuardExpression guard = GuardExpression.of("""
                {
                  "type": "ALL",
                  "conditions": [
                    { "operator": "GTE", "path": "applicant.age", "value": 18 },
                    { "operator": "EQ", "path": "applicant.name", "value": "Ali" },
                    { "operator": "EQ", "path": "applicant.verified", "value": true },
                    { "operator": "GT", "path": "score", "value": 75.5 }
                  ]
                }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    @DisplayName("Should reject numeric comparison on string property")
    void shouldRejectNumericComparisonOnStringProperty() {
        GuardExpression guard = GuardExpression.of("""
                { "operator": "GT", "path": "applicant.name", "value": 100 }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isFalse();
        assertThat(report.issues()).anyMatch(issue ->
                issue.contains("incompatible with schema type 'string'")
        );
    }

    @Test
    @DisplayName("Should reject undeclared property when additionalProperties is false")
    void shouldRejectUndeclaredProperty() {
        GuardExpression guard = GuardExpression.of("""
                { "operator": "EQ", "path": "applicant.creditScore", "value": 700 }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isFalse();
        assertThat(report.issues()).anyMatch(issue ->
                issue.contains("references undeclared property 'creditScore'")
        );
    }

    @Test
    @DisplayName("Should allow undeclared property when additionalProperties is open")
    void shouldAllowUndeclaredPropertyWhenOpen() {
        GuardExpression guard = GuardExpression.of("""
                { "operator": "EQ", "path": "metadata.customTag", "value": "VIP" }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isTrue();
    }

    @Test
    @DisplayName("Should reject IN list containing elements incompatible with property type")
    void shouldRejectIncompatibleElementsInList() {
        GuardExpression guard = GuardExpression.of("""
                { "operator": "IN", "path": "applicant.status", "value": [10, 20] }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isFalse();
        assertThat(report.issues()).anyMatch(issue ->
                issue.contains("has type Number which is incompatible with schema type 'string'")
        );
    }

    @Test
    @DisplayName("Should allow unary EXISTS operator on any property type")
    void shouldAllowUnaryExistsOperator() {
        GuardExpression guard = GuardExpression.of("""
                { "operator": "EXISTS", "path": "applicant.status" }
                """);

        GuardCompatibilityReport report = GuardSchemaCompatibilityAnalyzer.analyze(guard, strictSchema);

        assertThat(report.compatible()).isTrue();
    }
}
