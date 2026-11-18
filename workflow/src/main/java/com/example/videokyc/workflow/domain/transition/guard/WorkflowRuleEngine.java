package com.example.videokyc.workflow.domain.transition.guard;

import com.example.videokyc.workflow.domain.shared.DomainException;
import com.example.videokyc.workflow.domain.version.ContextSchema;
import com.example.videokyc.workflow.domain.version.schema.ContextValidationReport;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain rule engine providing structured, deterministic, and side-effect-free evaluation
 * of {@link GuardExpression} rules against runtime business context documents.
 */
public final class WorkflowRuleEngine {

    private WorkflowRuleEngine() {
    }

    /**
     * Evaluates a {@link GuardExpression} against a runtime JSON context.
     *
     * @param guard   the guard expression (if null, unconditionally matches)
     * @param context the consumer-supplied runtime context data
     * @return a structured diagnostic evaluation result
     */
    public static GuardEvaluationResult evaluate(GuardExpression guard, JsonNode context) {
        return evaluate(guard, context, null);
    }

    /**
     * Evaluates a {@link GuardExpression} against a runtime JSON context, validating
     * the context against the specified {@link ContextSchema} prior to guard execution.
     *
     * @param guard   the guard expression (if null, unconditionally matches)
     * @param context the consumer-supplied runtime context data
     * @param schema  optional version context schema (if present, validated before evaluating guard)
     * @return a structured diagnostic evaluation result
     */
    public static GuardEvaluationResult evaluate(GuardExpression guard, JsonNode context, ContextSchema schema) {
        if (guard == null) {
            return GuardEvaluationResult.match(List.of());
        }

        if (context == null || context.isNull()) {
            return GuardEvaluationResult.error(
                    GuardEvaluationError.MALFORMED_CONTEXT,
                    "Runtime context is null or empty",
                    List.of()
            );
        }

        if (!context.isObject()) {
            return GuardEvaluationResult.error(
                    GuardEvaluationError.MALFORMED_CONTEXT,
                    "Runtime context must be a JSON object",
                    List.of()
            );
        }

        if (schema != null) {
            ContextValidationReport schemaReport = schema.validate(context);
            if (!schemaReport.valid()) {
                String firstError = schemaReport.errors().isEmpty()
                        ? "Context violates schema"
                        : schemaReport.errors().get(0).message();
                return GuardEvaluationResult.error(
                        GuardEvaluationError.SCHEMA_VIOLATION,
                        "Context schema validation failed: " + firstError,
                        List.of()
                );
            }
        }

        List<String> evaluatedPaths = new ArrayList<>();
        return evaluateNode(guard.rootNode(), context, evaluatedPaths);
    }

    private static GuardEvaluationResult evaluateNode(GuardNode node, JsonNode context, List<String> paths) {
        if (node instanceof GuardNode.Logical logical) {
            return evaluateLogical(logical, context, paths);
        } else if (node instanceof GuardNode.Leaf leaf) {
            return evaluateLeaf(leaf, context, paths);
        }
        return GuardEvaluationResult.error(
                GuardEvaluationError.EVALUATION_ERROR,
                "Unsupported guard node type: " + node.getClass().getSimpleName(),
                paths
        );
    }

    private static GuardEvaluationResult evaluateLogical(GuardNode.Logical logical, JsonNode context, List<String> paths) {
        return switch (logical.operator()) {
            case ALL -> {
                for (GuardNode child : logical.children()) {
                    GuardEvaluationResult childRes = evaluateNode(child, context, paths);
                    if (childRes.hasError()) {
                        yield childRes;
                    }
                    if (!childRes.matched()) {
                        yield GuardEvaluationResult.noMatch(
                                "ALL condition failed: " + childRes.reason(),
                                paths
                        );
                    }
                }
                yield GuardEvaluationResult.match(paths);
            }
            case ANY -> {
                StringBuilder reasons = new StringBuilder();
                for (GuardNode child : logical.children()) {
                    GuardEvaluationResult childRes = evaluateNode(child, context, paths);
                    if (childRes.matched()) {
                        yield GuardEvaluationResult.match(paths);
                    }
                    if (childRes.hasError()) {
                        yield childRes;
                    }
                    if (!reasons.isEmpty()) {
                        reasons.append("; ");
                    }
                    reasons.append(childRes.reason());
                }
                yield GuardEvaluationResult.noMatch("ANY condition failed: [" + reasons + "]", paths);
            }
            case NOT -> {
                GuardEvaluationResult childRes = evaluateNode(logical.children().get(0), context, paths);
                if (childRes.hasError()) {
                    yield childRes;
                }
                if (childRes.matched()) {
                    yield GuardEvaluationResult.noMatch("NOT condition failed: inner condition matched", paths);
                } else {
                    yield GuardEvaluationResult.match(paths);
                }
            }
        };
    }

    private static GuardEvaluationResult evaluateLeaf(GuardNode.Leaf leaf, JsonNode context, List<String> paths) {
        paths.add(leaf.path());
        try {
            boolean matched = GuardEvaluator.evaluate(leaf, context);
            if (matched) {
                return GuardEvaluationResult.match(paths);
            } else {
                return GuardEvaluationResult.noMatch(
                        "Condition failed: path '" + leaf.path() + "' " + leaf.operator() + " " + leaf.value(),
                        paths
                );
            }
        } catch (DomainException e) {
            GuardEvaluationError err = switch (e.getErrorCode()) {
                case "TYPE_MISMATCH" -> GuardEvaluationError.TYPE_MISMATCH;
                case "PATH_NOT_FOUND" -> GuardEvaluationError.PATH_NOT_FOUND;
                default -> GuardEvaluationError.EVALUATION_ERROR;
            };
            return GuardEvaluationResult.error(err, e.getMessage(), paths);
        }
    }
}
