package com.example.videokyc.workflow.domain.version.schema;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextSchemaValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String validKycSchemaJson = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "properties": {
                "applicant": {
                  "type": "object",
                  "properties": {
                    "age": {
                      "type": "integer",
                      "minimum": 18,
                      "maximum": 120
                    },
                    "nationalId": {
                      "type": "string",
                      "minLength": 10,
                      "maxLength": 10
                    },
                    "status": {
                      "type": "string",
                      "enum": ["ACTIVE", "PENDING", "SUSPENDED"]
                    }
                  },
                  "required": ["age", "nationalId"],
                  "additionalProperties": false
                },
                "score": {
                  "type": "number",
                  "exclusiveMinimum": 0.0,
                  "exclusiveMaximum": 100.0
                }
              },
              "required": ["applicant"],
              "additionalProperties": false
            }
            """;

    @Test
    @DisplayName("Should validate valid Draft 2020-12 schema without exception")
    void shouldValidateValidSchema() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        ContextSchemaValidator.validateSchema(schema);
        schema.validateSelf();
    }

    @Test
    @DisplayName("Should reject unsupported schema dialect")
    void shouldRejectUnsupportedDialect() {
        String legacySchema = """
                {
                  "$schema": "http://json-schema.org/draft-04/schema#",
                  "type": "object"
                }
                """;
        ContextSchema schema = ContextSchema.of(legacySchema);

        assertThatThrownBy(() -> ContextSchemaValidator.validateSchema(schema))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Only JSON Schema Draft 2020-12 is supported");
    }

    @Test
    @DisplayName("Should reject schema containing remote network $ref pointers")
    void shouldRejectRemoteRef() {
        String remoteRefSchema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "properties": {
                    "remoteUser": {
                      "$ref": "https://attacker.example.com/schema/user.json"
                    }
                  }
                }
                """;
        ContextSchema schema = ContextSchema.of(remoteRefSchema);

        assertThatThrownBy(() -> ContextSchemaValidator.validateSchema(schema))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Remote $ref resolution is disabled for security");
    }

    @Test
    @DisplayName("Should reject schema exceeding maximum depth limit")
    void shouldRejectDeeplyNestedSchema() {
        StringBuilder deep = new StringBuilder("{\"type\": \"object\"");
        for (int i = 0; i < 12; i++) {
            deep.append(", \"properties\": {\"nested\": {\"type\": \"object\"");
        }
        for (int i = 0; i < 12; i++) {
            deep.append("}}");
        }
        deep.append("}");

        ContextSchema schema = ContextSchema.of(deep.toString());

        assertThatThrownBy(() -> ContextSchemaValidator.validateSchema(schema))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("nesting depth exceeds maximum limit");
    }

    @Test
    @DisplayName("Should validate compliant context data successfully")
    void shouldValidateCompliantContext() throws Exception {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String validDataJson = """
                {
                  "applicant": {
                    "age": 25,
                    "nationalId": "1234567890",
                    "status": "ACTIVE"
                  },
                  "score": 85.5
                }
                """;

        ContextValidationReport report = schema.validate(validDataJson);

        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should report missing required fields")
    void shouldReportMissingRequiredFields() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String missingDataJson = """
                {
                  "applicant": {
                    "age": 25
                  }
                }
                """;

        ContextValidationReport report = schema.validate(missingDataJson);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e ->
                e.errorCode().equals("REQUIRED_FIELD_MISSING") && e.path().equals("applicant.nationalId")
        );
    }

    @Test
    @DisplayName("Should report type mismatch errors")
    void shouldReportTypeMismatch() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String badTypeJson = """
                {
                  "applicant": {
                    "age": "twenty-five",
                    "nationalId": "1234567890"
                  }
                }
                """;

        ContextValidationReport report = schema.validate(badTypeJson);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e ->
                e.errorCode().equals("TYPE_MISMATCH") && e.path().equals("applicant.age")
        );
    }

    @Test
    @DisplayName("Should reject unrecognized property when additionalProperties is false")
    void shouldRejectUnrecognizedProperty() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String extraPropertyJson = """
                {
                  "applicant": {
                    "age": 30,
                    "nationalId": "1234567890",
                    "hackedField": true
                  }
                }
                """;

        ContextValidationReport report = schema.validate(extraPropertyJson);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e ->
                e.errorCode().equals("UNRECOGNIZED_PROPERTY") && e.path().equals("applicant.hackedField")
        );
    }

    @Test
    @DisplayName("Should report numeric bound violations (minimum and maximum)")
    void shouldReportNumericBoundViolations() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String boundViolationJson = """
                {
                  "applicant": {
                    "age": 16,
                    "nationalId": "1234567890"
                  },
                  "score": 105.0
                }
                """;

        ContextValidationReport report = schema.validate(boundViolationJson);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e -> e.errorCode().equals("MINIMUM_VIOLATION"));
        assertThat(report.errors()).anyMatch(e -> e.errorCode().equals("EXCLUSIVE_MAXIMUM_VIOLATION"));
    }

    @Test
    @DisplayName("Should report enum mismatch error")
    void shouldReportEnumMismatch() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        String enumViolationJson = """
                {
                  "applicant": {
                    "age": 30,
                    "nationalId": "1234567890",
                    "status": "UNKNOWN_STATUS"
                  }
                }
                """;

        ContextValidationReport report = schema.validate(enumViolationJson);

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e ->
                e.errorCode().equals("ENUM_MISMATCH") && e.path().equals("applicant.status")
        );
    }

    @Test
    @DisplayName("Should reject non-object context payload")
    void shouldRejectNonObjectContext() {
        ContextSchema schema = ContextSchema.of(validKycSchemaJson);
        ContextValidationReport report = schema.validate("\"simple string\"");

        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(e -> e.errorCode().equals("INVALID_CONTEXT_ROOT"));
    }
}
