package com.example.videokyc.workflow.domain.transition.guard;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Parser and structural validator for the restricted Guard JSON DSL.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Maximum depth: 8</li>
 *   <li>Maximum nodes: 100</li>
 *   <li>Maximum JSON size: 32 KB (32,768 bytes)</li>
 *   <li>Valid simple dotted path: {@code ^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$}</li>
 * </ul>
 */
public final class GuardParser {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 100;
    public static final int MAX_SIZE_BYTES = 32_768;
    private static final Pattern PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)*$");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GuardParser() {
    }

    public static GuardNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new DomainException("INVALID_GUARD_EXPRESSION", "Guard JSON must not be blank");
        }
        if (json.length() > MAX_SIZE_BYTES) {
            throw new DomainException("GUARD_SIZE_EXCEEDED", "Guard JSON exceeds maximum limit of 32 KB");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new DomainException("INVALID_GUARD_JSON", "Guard JSON is malformed: " + e.getMessage(), e);
        }

        int[] nodeCount = new int[]{0};
        return parseNode(root, 1, nodeCount);
    }

    private static GuardNode parseNode(JsonNode node, int depth, int[] nodeCount) {
        if (depth > MAX_DEPTH) {
            throw new DomainException("GUARD_DEPTH_EXCEEDED", "Guard AST depth exceeds maximum allowed limit of " + MAX_DEPTH);
        }
        nodeCount[0]++;
        if (nodeCount[0] > MAX_NODES) {
            throw new DomainException("GUARD_NODE_LIMIT_EXCEEDED", "Guard AST node count exceeds maximum allowed limit of " + MAX_NODES);
        }

        if (!node.isObject()) {
            throw new DomainException("INVALID_GUARD_NODE", "Guard AST node must be a JSON object");
        }

        if (node.hasNonNull("type")) {
            String typeStr = node.get("type").asText();
            LogicalOperator op;
            try {
                op = LogicalOperator.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DomainException("UNKNOWN_GUARD_LOGICAL_OPERATOR", "Unsupported logical operator: " + typeStr);
            }

            if (op == LogicalOperator.NOT) {
                JsonNode childNode = node.hasNonNull("condition") ? node.get("condition") :
                        (node.hasNonNull("conditions") && node.get("conditions").isArray() && node.get("conditions").size() == 1
                                ? node.get("conditions").get(0) : null);
                if (childNode == null) {
                    throw new DomainException("INVALID_NOT_OPERATOR", "NOT operator must have exactly one child condition");
                }
                GuardNode child = parseNode(childNode, depth + 1, nodeCount);
                return new GuardNode.Logical(LogicalOperator.NOT, List.of(child));
            } else {
                JsonNode conditionsNode = node.get("conditions");
                if (conditionsNode == null || !conditionsNode.isArray() || conditionsNode.isEmpty()) {
                    throw new DomainException("EMPTY_LOGICAL_CONDITIONS", op + " operator must have non-empty conditions array");
                }
                List<GuardNode> children = new ArrayList<>();
                for (JsonNode item : conditionsNode) {
                    children.add(parseNode(item, depth + 1, nodeCount));
                }
                return new GuardNode.Logical(op, children);
            }
        } else if (node.hasNonNull("path") && node.hasNonNull("operator")) {
            String path = node.get("path").asText().trim();
            if (!PATH_PATTERN.matcher(path).matches()) {
                throw new DomainException("INVALID_GUARD_PATH", "Guard path is invalid: " + path);
            }

            String opStr = node.get("operator").asText();
            ComparisonOperator op;
            try {
                op = ComparisonOperator.valueOf(opStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new DomainException("UNKNOWN_GUARD_COMPARISON_OPERATOR", "Unsupported comparison operator: " + opStr);
            }

            Object value = null;
            if (!op.isUnary()) {
                JsonNode valNode = node.get("value");
                if (valNode == null || valNode.isNull()) {
                    throw new DomainException("MISSING_GUARD_COMPARISON_VALUE",
                            "Operator " + op + " requires a non-null comparison value");
                }
                value = extractJsonValue(valNode);
            }

            return new GuardNode.Leaf(path, op, value);
        } else {
            throw new DomainException("INVALID_GUARD_NODE_STRUCTURE",
                    "Guard node must have either 'type' (logical) or 'path' and 'operator' (comparison)");
        }
    }

    private static Object extractJsonValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isInt()) {
            return node.asInt();
        } else if (node.isLong()) {
            return node.asLong();
        } else if (node.isDouble() || node.isFloat() || node.isBigDecimal()) {
            return node.asDouble();
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode elem : node) {
                list.add(extractJsonValue(elem));
            }
            return list;
        }
        return node.toString();
    }
}
