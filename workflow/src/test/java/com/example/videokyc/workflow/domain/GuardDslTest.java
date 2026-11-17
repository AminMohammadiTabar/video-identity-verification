package com.example.videokyc.workflow.domain;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.transition.guard.GuardEvaluator;
import com.example.videokyc.workflow.domain.transition.guard.GuardNode;
import com.example.videokyc.workflow.domain.transition.guard.GuardParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardDslTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Should parse complex nested Guard AST and evaluate deterministically")
    void shouldParseAndEvaluateNestedGuard() throws Exception {
        String guardJson = """
                {
                  "type": "ALL",
                  "conditions": [
                    {
                      "path": "verification.documentsComplete",
                      "operator": "EQ",
                      "value": true
                    },
                    {
                      "type": "ANY",
                      "conditions": [
                        {
                          "path": "verification.riskScore",
                          "operator": "LTE",
                          "value": 50
                        },
                        {
                          "path": "verification.manualApproval",
                          "operator": "EQ",
                          "value": true
                        }
                      ]
                    }
                  ]
                }
                """;

        GuardNode ast = GuardParser.parse(guardJson);
        assertThat(ast).isNotNull();

        // Context 1: Passing with low risk score
        JsonNode context1 = mapper.readTree("""
                {
                  "verification": {
                    "documentsComplete": true,
                    "riskScore": 45,
                    "manualApproval": false
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, context1)).isTrue();

        // Context 2: Passing with high risk score but manual approval true
        JsonNode context2 = mapper.readTree("""
                {
                  "verification": {
                    "documentsComplete": true,
                    "riskScore": 85,
                    "manualApproval": true
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, context2)).isTrue();

        // Context 3: Failing because documentsComplete is false
        JsonNode context3 = mapper.readTree("""
                {
                  "verification": {
                    "documentsComplete": false,
                    "riskScore": 20,
                    "manualApproval": true
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, context3)).isFalse();

        // Context 4: Failing because riskScore high and manualApproval false
        JsonNode context4 = mapper.readTree("""
                {
                  "verification": {
                    "documentsComplete": true,
                    "riskScore": 90,
                    "manualApproval": false
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, context4)).isFalse();
    }

    @Test
    @DisplayName("Should evaluate NOT, EXISTS, NOT_EXISTS, and IN operators correctly")
    void shouldEvaluateUnaryAndSetOperators() throws Exception {
        String guardJson = """
                {
                  "type": "ALL",
                  "conditions": [
                    {
                      "path": "user.email",
                      "operator": "EXISTS"
                    },
                    {
                      "path": "user.blockedFlag",
                      "operator": "NOT_EXISTS"
                    },
                    {
                      "path": "user.country",
                      "operator": "IN",
                      "value": ["IR", "DE", "FR"]
                    },
                    {
                      "type": "NOT",
                      "condition": {
                        "path": "user.isSanctioned",
                        "operator": "EQ",
                        "value": true
                      }
                    }
                  ]
                }
                """;

        GuardNode ast = GuardParser.parse(guardJson);

        JsonNode validContext = mapper.readTree("""
                {
                  "user": {
                    "email": "user@example.com",
                    "country": "IR",
                    "isSanctioned": false
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, validContext)).isTrue();

        JsonNode invalidCountryContext = mapper.readTree("""
                {
                  "user": {
                    "email": "user@example.com",
                    "country": "US",
                    "isSanctioned": false
                  }
                }
                """);
        assertThat(GuardEvaluator.evaluate(ast, invalidCountryContext)).isFalse();
    }

    @Test
    @DisplayName("Should prohibit implicit type coercion in comparisons")
    void shouldProhibitImplicitCoercion() throws Exception {
        String guardJson = """
                {
                  "path": "metrics.score",
                  "operator": "GT",
                  "value": 50
                }
                """;
        GuardNode ast = GuardParser.parse(guardJson);

        // String value instead of number -> must throw TYPE_MISMATCH
        JsonNode stringValueContext = mapper.readTree("""
                {
                  "metrics": {
                    "score": "75"
                  }
                }
                """);
        assertThatThrownBy(() -> GuardEvaluator.evaluate(ast, stringValueContext))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Numeric comparison requires numeric values");
    }

    @Test
    @DisplayName("Should reject Guard JSON exceeding depth or size limits")
    void shouldEnforceGuardSafetyLimits() {
        // Exceeding max depth 8
        String deepJson = """
                {"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"type":"ALL","conditions":[{"path":"a","operator":"EXISTS"}]}]}]}]}]}]}]}]}]}
                """;
        assertThatThrownBy(() -> GuardParser.parse(deepJson))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("depth exceeds maximum allowed limit");

        // Invalid path
        String invalidPathJson = """
                {
                  "path": "user.invalid[0].prop",
                  "operator": "EXISTS"
                }
                """;
        assertThatThrownBy(() -> GuardParser.parse(invalidPathJson))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Guard path is invalid");
    }
}
