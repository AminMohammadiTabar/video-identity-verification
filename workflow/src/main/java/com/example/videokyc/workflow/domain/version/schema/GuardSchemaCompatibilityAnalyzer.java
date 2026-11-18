package com.example.videokyc.workflow.domain.version.schema;

import com.example.videokyc.workflow.domain.transition.guard.ComparisonOperator;
import com.example.videokyc.workflow.domain.transition.guard.GuardExpression;
import com.example.videokyc.workflow.domain.transition.guard.GuardNode;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Static analyzer verifying semantic and type compatibility between a
 * {@link GuardExpression} AST and a {@link ContextSchema} document before publication.
 * <p>
 * Ensures that Guard paths reference declared schema properties and that leaf comparison operators
 * match the declared JSON Schema types (e.g. numeric operators applied only to number/integer fields).
 */
public final class GuardSchemaCompatibilityAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GuardSchemaCompatibilityAnalyzer() {
    }

    /**
     * Statically analyzes a {@link GuardExpression} against a {@link ContextSchema}.
     *
     * @param guard  the guard expression (can be null if transition has no guard)
     * @param schema the context schema (must not be null)
     * @return a diagnostic report containing any detected incompatibility issues
     */
    public static GuardCompatibilityReport analyze(GuardExpression guard, ContextSchema schema) {
        if (guard == null) {
            return GuardCompatibilityReport.success();
        }
        if (schema == null) {
            return GuardCompatibilityReport.failure(List.of("Context schema must not be null"));
        }

        JsonNode schemaRoot;
        try {
            schemaRoot = MAPPER.readTree(schema.schemaJson());
        } catch (JsonProcessingException e) {
            return GuardCompatibilityReport.failure(List.of("Context schema JSON is malformed: " + e.getMessage()));
        }

        List<String> issues = new ArrayList<>();
        analyzeNode(guard.rootNode(), schemaRoot, issues);

        return issues.isEmpty() ? GuardCompatibilityReport.success() : GuardCompatibilityReport.failure(issues);
    }

    private static void analyzeNode(GuardNode node, JsonNode schemaRoot, List<String> issues) {
        if (node instanceof GuardNode.Logical logical) {
            for (GuardNode child : logical.children()) {
                analyzeNode(child, schemaRoot, issues);
            }
        } else if (node instanceof GuardNode.Leaf leaf) {
            analyzeLeaf(leaf, schemaRoot, issues);
        }
    }

    private static void analyzeLeaf(GuardNode.Leaf leaf, JsonNode schemaRoot, List<String> issues) {
        String path = leaf.path();
        String[] segments = path.split("\\.");

        JsonNode current = schemaRoot;
        boolean fullyResolved = true;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (current == null) {
                fullyResolved = false;
                break;
            }

            JsonNode properties = current.get("properties");
            if (properties != null && properties.has(segment)) {
                current = properties.get(segment);
            } else {
                // Segment not explicitly declared in properties
                boolean additionalAllowed = true;
                if (current.has("additionalProperties") && current.get("additionalProperties").isBoolean()) {
                    additionalAllowed = current.get("additionalProperties").asBoolean();
                }

                if (!additionalAllowed) {
                    issues.add("Guard references undeclared property '" + segment + "' along path '" + path
                            + "' where additionalProperties is false");
                    return;
                }

                // If additional properties are allowed, the schema is open and remaining path cannot be statically resolved
                fullyResolved = false;
                break;
            }
        }

        if (fullyResolved && current != null) {
            validateOperatorTypeCompatibility(leaf, current, issues);
        }
    }

    private static void validateOperatorTypeCompatibility(GuardNode.Leaf leaf, JsonNode schemaPropertyNode, List<String> issues) {
        ComparisonOperator op = leaf.operator();
        String path = leaf.path();

        if (op.isUnary()) {
            // EXISTS and NOT_EXISTS are valid on all property types
            return;
        }

        if (!schemaPropertyNode.has("type") || !schemaPropertyNode.get("type").isTextual()) {
            return;
        }

        String declaredType = schemaPropertyNode.get("type").asText().toLowerCase();
        Object value = leaf.value();

        // 1. Relational numeric operators (GT, GTE, LT, LTE)
        if (op == ComparisonOperator.GT || op == ComparisonOperator.GTE
                || op == ComparisonOperator.LT || op == ComparisonOperator.LTE) {
            if (!declaredType.equals("integer") && !declaredType.equals("number")) {
                issues.add("Numeric operator '" + op + "' at path '" + path
                        + "' is incompatible with schema type '" + declaredType + "'");
            }
            if (value != null && !(value instanceof Number)) {
                issues.add("Numeric operator '" + op + "' at path '" + path
                        + "' requires a numeric comparison value, but received: " + value.getClass().getSimpleName());
            }
            return;
        }

        // 2. Equality and Inequality operators (EQ, NE)
        if (op == ComparisonOperator.EQ || op == ComparisonOperator.NE) {
            if (value instanceof Boolean && !declaredType.equals("boolean")) {
                issues.add("Boolean comparison '" + op + "' at path '" + path
                        + "' is incompatible with schema type '" + declaredType + "'");
            } else if (value instanceof String && !declaredType.equals("string")) {
                issues.add("String comparison '" + op + "' at path '" + path
                        + "' is incompatible with schema type '" + declaredType + "'");
            } else if (value instanceof Number && !declaredType.equals("integer") && !declaredType.equals("number")) {
                issues.add("Numeric comparison '" + op + "' at path '" + path
                        + "' is incompatible with schema type '" + declaredType + "'");
            }
            return;
        }

        // 3. Set membership operators (IN, NOT_IN)
        if (op == ComparisonOperator.IN || op == ComparisonOperator.NOT_IN) {
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String && !declaredType.equals("string")) {
                        issues.add("Element in '" + op + "' list at path '" + path
                                + "' has type String which is incompatible with schema type '" + declaredType + "'");
                        break;
                    } else if (item instanceof Number && !declaredType.equals("integer") && !declaredType.equals("number")) {
                        issues.add("Element in '" + op + "' list at path '" + path
                                + "' has type Number which is incompatible with schema type '" + declaredType + "'");
                        break;
                    } else if (item instanceof Boolean && !declaredType.equals("boolean")) {
                        issues.add("Element in '" + op + "' list at path '" + path
                                + "' has type Boolean which is incompatible with schema type '" + declaredType + "'");
                        break;
                    }
                }
            } else {
                issues.add("Operator '" + op + "' at path '" + path + "' requires a list value");
            }
        }
    }
}
