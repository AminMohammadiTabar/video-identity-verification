package com.example.videokyc.workflow.domain.transition.guard;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.Objects;

/**
 * Deterministic, side-effect-free evaluator for Guard AST nodes against a JSON business context.
 * <p>
 * Constraints:
 * <ul>
 *   <li>No implicit type coercion between String, Number, Boolean.</li>
 *   <li>Missing leaf property makes comparison evaluate to false (or true for NE).</li>
 *   <li>Pure in-memory evaluation; never performs network or disk I/O.</li>
 * </ul>
 */
public final class GuardEvaluator {

    private GuardEvaluator() {
    }

    public static boolean evaluate(GuardNode node, JsonNode context) {
        Objects.requireNonNull(node, "Guard node must not be null");
        if (context == null || context.isNull()) {
            return false;
        }

        if (node instanceof GuardNode.Logical logical) {
            return evaluateLogical(logical, context);
        } else if (node instanceof GuardNode.Leaf leaf) {
            return evaluateLeaf(leaf, context);
        }
        throw new DomainException("UNSUPPORTED_GUARD_NODE", "Unsupported guard node type: " + node.getClass().getName());
    }

    private static boolean evaluateLogical(GuardNode.Logical logical, JsonNode context) {
        return switch (logical.operator()) {
            case ALL -> {
                for (GuardNode child : logical.children()) {
                    if (!evaluate(child, context)) {
                        yield false;
                    }
                }
                yield true;
            }
            case ANY -> {
                for (GuardNode child : logical.children()) {
                    if (evaluate(child, context)) {
                        yield true;
                    }
                }
                yield false;
            }
            case NOT -> !evaluate(logical.children().get(0), context);
        };
    }

    private static boolean evaluateLeaf(GuardNode.Leaf leaf, JsonNode context) {
        String path = leaf.path();
        ComparisonOperator op = leaf.operator();
        JsonNode targetNode = resolvePath(context, path);

        if (op == ComparisonOperator.EXISTS) {
            return targetNode != null;
        }
        if (op == ComparisonOperator.NOT_EXISTS) {
            return targetNode == null;
        }

        if (targetNode == null) {
            return op == ComparisonOperator.NE;
        }

        Object expected = leaf.value();

        return switch (op) {
            case EQ -> compareEquality(targetNode, expected);
            case NE -> !compareEquality(targetNode, expected);
            case GT -> compareNumeric(targetNode, expected) > 0;
            case GTE -> compareNumeric(targetNode, expected) >= 0;
            case LT -> compareNumeric(targetNode, expected) < 0;
            case LTE -> compareNumeric(targetNode, expected) <= 0;
            case IN -> evaluateIn(targetNode, expected);
            case NOT_IN -> !evaluateIn(targetNode, expected);
            default -> throw new DomainException("UNSUPPORTED_COMPARISON", "Unsupported operator: " + op);
        };
    }

    private static JsonNode resolvePath(JsonNode root, String path) {
        String[] segments = path.split("\\.");
        JsonNode current = root;
        for (String segment : segments) {
            if (current == null || !current.isObject() || !current.has(segment)) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private static boolean compareEquality(JsonNode node, Object expected) {
        if (node.isNull()) {
            return expected == null;
        }
        if (expected instanceof Boolean b && node.isBoolean()) {
            return node.asBoolean() == b;
        }
        if (expected instanceof Number n && node.isNumber()) {
            return Double.compare(node.asDouble(), n.doubleValue()) == 0;
        }
        if (expected instanceof String s && node.isTextual()) {
            return Objects.equals(node.asText(), s);
        }
        return false;
    }

    private static int compareNumeric(JsonNode node, Object expected) {
        if (!node.isNumber() || !(expected instanceof Number num)) {
            throw new DomainException("TYPE_MISMATCH",
                    "Numeric comparison requires numeric values for both property and expected value");
        }
        return Double.compare(node.asDouble(), num.doubleValue());
    }

    private static boolean evaluateIn(JsonNode node, Object expected) {
        if (expected instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (compareEquality(node, item)) {
                    return true;
                }
            }
            return false;
        }
        throw new DomainException("INVALID_IN_VALUE", "IN operator requires a collection or array value");
    }
}
