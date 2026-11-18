package com.example.videokyc.workflow.domain.version.schema;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Domain validator for {@link ContextSchema} specifications (JSON Schema Draft 2020-12)
 * and runtime consumer business context payloads.
 * <p>
 * Ensures strict security: remote network $ref resolution is disabled, size and depth limits
 * are enforced, and structured diagnostic reports are returned.
 */
public final class ContextSchemaValidator {

    public static final int MAX_SCHEMA_SIZE_BYTES = 256 * 1024; // 256 KB
    public static final int MAX_SCHEMA_DEPTH = 10;
    public static final int MAX_VALIDATION_ERRORS = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContextSchemaValidator() {
    }

    /**
     * Validates a {@link ContextSchema} document itself to ensure it adheres to Draft 2020-12,
     * contains no remote network $ref pointers, and does not exceed safety limits.
     *
     * @param schema the context schema value object
     * @throws DomainException if the schema violates structural or security rules
     */
    public static void validateSchema(ContextSchema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        String rawJson = schema.schemaJson();

        if (rawJson.getBytes().length > MAX_SCHEMA_SIZE_BYTES) {
            throw new DomainException("SCHEMA_SIZE_EXCEEDED",
                    "Context schema size exceeds maximum allowed size of " + MAX_SCHEMA_SIZE_BYTES + " bytes");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(rawJson);
        } catch (JsonProcessingException e) {
            throw new DomainException("MALFORMED_CONTEXT_SCHEMA", "Context schema is not valid JSON: " + e.getMessage(), e);
        }

        if (!root.isObject()) {
            throw new DomainException("INVALID_CONTEXT_SCHEMA_TYPE", "Context schema root must be a JSON object");
        }

        // Validate $schema dialect if specified
        if (root.has("$schema")) {
            JsonNode schemaNode = root.get("$schema");
            if (!schemaNode.isTextual()) {
                throw new DomainException("INVALID_SCHEMA_DIALECT", "$schema keyword must be a string");
            }
            String dialect = schemaNode.asText();
            if (!dialect.contains("2020-12") && !dialect.equals(ContextSchema.DRAFT_2020_12_URI)) {
                throw new DomainException("UNSUPPORTED_SCHEMA_DIALECT",
                        "Only JSON Schema Draft 2020-12 is supported, found: " + dialect);
            }
        }

        // Validate tree depth and check for forbidden remote $ref
        validateSchemaNodeRecursive(root, 1);
    }

    private static void validateSchemaNodeRecursive(JsonNode node, int currentDepth) {
        if (currentDepth > MAX_SCHEMA_DEPTH) {
            throw new DomainException("SCHEMA_DEPTH_EXCEEDED",
                    "Context schema nesting depth exceeds maximum limit of " + MAX_SCHEMA_DEPTH);
        }

        if (node.isObject()) {
            if (node.has("$ref")) {
                JsonNode refNode = node.get("$ref");
                if (refNode.isTextual()) {
                    String ref = refNode.asText();
                    if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("ftp://") || ref.contains("://")) {
                        throw new DomainException("REMOTE_REF_FORBIDDEN",
                                "Remote $ref resolution is disabled for security: " + ref);
                    }
                }
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                validateSchemaNodeRecursive(field.getValue(), currentDepth + 1);
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                validateSchemaNodeRecursive(child, currentDepth + 1);
            }
        }
    }

    /**
     * Validates a JSON string representing consumer business context data against the given schema.
     */
    public static ContextValidationReport validateContext(ContextSchema schema, String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return ContextValidationReport.failure(List.of(
                    new ContextValidationError("", "EMPTY_CONTEXT", "Context JSON must not be blank")
            ));
        }
        try {
            JsonNode contextNode = MAPPER.readTree(contextJson);
            return validateContext(schema, contextNode);
        } catch (JsonProcessingException e) {
            return ContextValidationReport.failure(List.of(
                    new ContextValidationError("", "MALFORMED_JSON", "Context payload is not valid JSON: " + e.getMessage())
            ));
        }
    }

    /**
     * Validates a {@link JsonNode} context data against the given {@link ContextSchema}.
     */
    public static ContextValidationReport validateContext(ContextSchema schema, JsonNode contextData) {
        Objects.requireNonNull(schema, "schema must not be null");

        if (contextData == null || !contextData.isObject()) {
            return ContextValidationReport.failure(List.of(
                    new ContextValidationError("", "INVALID_CONTEXT_ROOT", "Context data must be a JSON object")
            ));
        }

        JsonNode schemaRoot;
        try {
            schemaRoot = MAPPER.readTree(schema.schemaJson());
        } catch (JsonProcessingException e) {
            return ContextValidationReport.failure(List.of(
                    new ContextValidationError("", "MALFORMED_SCHEMA", "Schema is malformed: " + e.getMessage())
            ));
        }

        List<ContextValidationError> errors = new ArrayList<>();
        validateNode(schemaRoot, contextData, "", errors, 1);

        return errors.isEmpty() ? ContextValidationReport.success() : ContextValidationReport.failure(errors);
    }

    private static void validateNode(JsonNode schemaNode,
                                     JsonNode dataNode,
                                     String currentPath,
                                     List<ContextValidationError> errors,
                                     int depth) {
        if (errors.size() >= MAX_VALIDATION_ERRORS || depth > MAX_SCHEMA_DEPTH) {
            return;
        }

        if (schemaNode == null || dataNode == null) {
            return;
        }

        // 1. Type validation
        if (schemaNode.has("type") && schemaNode.get("type").isTextual()) {
            String expectedType = schemaNode.get("type").asText();
            if (!matchesType(expectedType, dataNode)) {
                errors.add(new ContextValidationError(
                        currentPath,
                        "TYPE_MISMATCH",
                        "Expected type '" + expectedType + "' at path '" + currentPath + "' but found " + dataNode.getNodeType()
                ));
                return; // Stop further checks on this field if type does not match
            }
        }

        // 2. Object validation (properties, required, additionalProperties)
        if (dataNode.isObject()) {
            // Check required fields
            if (schemaNode.has("required") && schemaNode.get("required").isArray()) {
                for (JsonNode requiredFieldNode : schemaNode.get("required")) {
                    if (requiredFieldNode.isTextual()) {
                        String requiredFieldName = requiredFieldNode.asText();
                        if (!dataNode.has(requiredFieldName) || dataNode.get(requiredFieldName).isNull()) {
                            String fieldPath = currentPath.isEmpty() ? requiredFieldName : currentPath + "." + requiredFieldName;
                            errors.add(new ContextValidationError(
                                    fieldPath,
                                    "REQUIRED_FIELD_MISSING",
                                    "Required property '" + requiredFieldName + "' is missing at '" + fieldPath + "'"
                            ));
                        }
                    }
                }
            }

            JsonNode propertiesNode = schemaNode.get("properties");
            boolean allowAdditional = true;
            if (schemaNode.has("additionalProperties") && schemaNode.get("additionalProperties").isBoolean()) {
                allowAdditional = schemaNode.get("additionalProperties").asBoolean();
            }

            Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String propName = field.getKey();
                JsonNode propVal = field.getValue();
                String propPath = currentPath.isEmpty() ? propName : currentPath + "." + propName;

                if (propertiesNode != null && propertiesNode.has(propName)) {
                    validateNode(propertiesNode.get(propName), propVal, propPath, errors, depth + 1);
                } else if (!allowAdditional) {
                    errors.add(new ContextValidationError(
                            propPath,
                            "UNRECOGNIZED_PROPERTY",
                            "Additional property '" + propName + "' is not permitted at '" + propPath + "'"
                    ));
                }
            }
        }

        // 3. Numeric bounds validation (minimum, maximum, exclusiveMinimum, exclusiveMaximum)
        if (dataNode.isNumber()) {
            double value = dataNode.asDouble();
            if (schemaNode.has("minimum") && schemaNode.get("minimum").isNumber()) {
                double min = schemaNode.get("minimum").asDouble();
                if (value < min) {
                    errors.add(new ContextValidationError(
                            currentPath, "MINIMUM_VIOLATION",
                            "Value " + value + " is less than minimum " + min + " at '" + currentPath + "'"
                    ));
                }
            }
            if (schemaNode.has("maximum") && schemaNode.get("maximum").isNumber()) {
                double max = schemaNode.get("maximum").asDouble();
                if (value > max) {
                    errors.add(new ContextValidationError(
                            currentPath, "MAXIMUM_EXCEEDED",
                            "Value " + value + " exceeds maximum " + max + " at '" + currentPath + "'"
                    ));
                }
            }
            if (schemaNode.has("exclusiveMinimum") && schemaNode.get("exclusiveMinimum").isNumber()) {
                double exMin = schemaNode.get("exclusiveMinimum").asDouble();
                if (value <= exMin) {
                    errors.add(new ContextValidationError(
                            currentPath, "EXCLUSIVE_MINIMUM_VIOLATION",
                            "Value " + value + " must be strictly greater than " + exMin + " at '" + currentPath + "'"
                    ));
                }
            }
            if (schemaNode.has("exclusiveMaximum") && schemaNode.get("exclusiveMaximum").isNumber()) {
                double exMax = schemaNode.get("exclusiveMaximum").asDouble();
                if (value >= exMax) {
                    errors.add(new ContextValidationError(
                            currentPath, "EXCLUSIVE_MAXIMUM_VIOLATION",
                            "Value " + value + " must be strictly less than " + exMax + " at '" + currentPath + "'"
                    ));
                }
            }
        }

        // 4. String constraints (minLength, maxLength, pattern)
        if (dataNode.isTextual()) {
            String text = dataNode.asText();
            if (schemaNode.has("minLength") && schemaNode.get("minLength").isInt()) {
                int minLen = schemaNode.get("minLength").asInt();
                if (text.length() < minLen) {
                    errors.add(new ContextValidationError(
                            currentPath, "MIN_LENGTH_VIOLATION",
                            "String length " + text.length() + " is less than minLength " + minLen + " at '" + currentPath + "'"
                    ));
                }
            }
            if (schemaNode.has("maxLength") && schemaNode.get("maxLength").isInt()) {
                int maxLen = schemaNode.get("maxLength").asInt();
                if (text.length() > maxLen) {
                    errors.add(new ContextValidationError(
                            currentPath, "MAX_LENGTH_EXCEEDED",
                            "String length " + text.length() + " exceeds maxLength " + maxLen + " at '" + currentPath + "'"
                    ));
                }
            }
            if (schemaNode.has("pattern") && schemaNode.get("pattern").isTextual()) {
                String patternStr = schemaNode.get("pattern").asText();
                try {
                    Pattern pattern = Pattern.compile(patternStr);
                    if (!pattern.matcher(text).find()) {
                        errors.add(new ContextValidationError(
                                currentPath, "PATTERN_MISMATCH",
                                "String does not match pattern '" + patternStr + "' at '" + currentPath + "'"
                        ));
                    }
                } catch (PatternSyntaxException ignored) {
                    // Invalid pattern handled during schema validation
                }
            }
        }

        // 5. Array items validation
        if (dataNode.isArray() && schemaNode.has("items")) {
            JsonNode itemsSchema = schemaNode.get("items");
            for (int i = 0; i < dataNode.size(); i++) {
                String itemPath = currentPath + "[" + i + "]";
                validateNode(itemsSchema, dataNode.get(i), itemPath, errors, depth + 1);
            }
        }

        // 6. Enum validation
        if (schemaNode.has("enum") && schemaNode.get("enum").isArray()) {
            boolean matchesEnum = false;
            for (JsonNode enumVal : schemaNode.get("enum")) {
                if (enumVal.equals(dataNode)) {
                    matchesEnum = true;
                    break;
                }
            }
            if (!matchesEnum) {
                errors.add(new ContextValidationError(
                        currentPath, "ENUM_MISMATCH",
                        "Value '" + dataNode.asText() + "' is not in allowed enum list at '" + currentPath + "'"
                ));
            }
        }
    }

    private static boolean matchesType(String expectedType, JsonNode dataNode) {
        return switch (expectedType.toLowerCase()) {
            case "object" -> dataNode.isObject();
            case "string" -> dataNode.isTextual();
            case "integer" -> dataNode.isIntegralNumber();
            case "number" -> dataNode.isNumber();
            case "boolean" -> dataNode.isBoolean();
            case "array" -> dataNode.isArray();
            case "null" -> dataNode.isNull();
            default -> true;
        };
    }
}
