package com.example.videokyc.workflow.domain.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Deterministic JSON canonicalizer and SHA-256 checksum calculator.
 * <p>
 * Ensures that workflow snapshot serialization is reproducible across platforms
 * by sorting all object keys lexicographically and producing compact UTF-8 JSON.
 */
public final class CanonicalJsonSerializer {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJsonSerializer() {
    }

    /**
     * Serializes any object into canonical JSON with sorted object keys.
     */
    public static String toCanonicalJson(Object value) {
        try {
            JsonNode tree = MAPPER.valueToTree(value);
            JsonNode canonicalTree = canonicalize(tree);
            return MAPPER.writeValueAsString(canonicalTree);
        } catch (JsonProcessingException e) {
            throw new DomainException("CANONICAL_SERIALIZATION_FAILED", "Failed to serialize object canonically", e);
        }
    }

    /**
     * Serializes a JSON string into canonical JSON with sorted object keys.
     */
    public static String canonicalizeJsonString(String rawJson) {
        try {
            JsonNode tree = MAPPER.readTree(rawJson);
            JsonNode canonicalTree = canonicalize(tree);
            return MAPPER.writeValueAsString(canonicalTree);
        } catch (JsonProcessingException e) {
            throw new DomainException("INVALID_JSON", "Invalid JSON string for canonicalization", e);
        }
    }

    /**
     * Computes the 64-character lowercase hexadecimal SHA-256 checksum over the UTF-8 bytes of canonical JSON.
     */
    public static String computeSha256Checksum(String canonicalJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm must be available", e);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> names = objectNode.fieldNames();
            while (names.hasNext()) {
                fieldNames.add(names.next());
            }
            Collections.sort(fieldNames);

            ObjectNode sortedObject = MAPPER.createObjectNode();
            for (String fieldName : fieldNames) {
                sortedObject.set(fieldName, canonicalize(objectNode.get(fieldName)));
            }
            return sortedObject;
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode canonicalArray = MAPPER.createArrayNode();
            for (JsonNode item : arrayNode) {
                canonicalArray.add(canonicalize(item));
            }
            return canonicalArray;
        }
        return node;
    }
}
