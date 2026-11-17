package com.example.videokyc.workflow.domain.version;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Value object representing a versioned JSON Schema (Draft 2020-12) used to validate
 * business context supplied by consumers at transition evaluation time.
 */
public record ContextSchema(String schemaJson) {
    public static final String DRAFT_2020_12_URI = "https://json-schema.org/draft/2020-12/schema";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ContextSchema {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new DomainException("INVALID_CONTEXT_SCHEMA", "Context schema JSON must not be blank");
        }
        try {
            JsonNode root = MAPPER.readTree(schemaJson);
            if (!root.isObject()) {
                throw new DomainException("INVALID_CONTEXT_SCHEMA_TYPE", "Context schema root must be a JSON object");
            }
        } catch (JsonProcessingException e) {
            throw new DomainException("MALFORMED_CONTEXT_SCHEMA", "Context schema JSON is malformed: " + e.getMessage(), e);
        }
        schemaJson = schemaJson.trim();
    }

    public static ContextSchema of(String schemaJson) {
        return new ContextSchema(schemaJson);
    }

    @Override
    public String toString() {
        return schemaJson;
    }
}
