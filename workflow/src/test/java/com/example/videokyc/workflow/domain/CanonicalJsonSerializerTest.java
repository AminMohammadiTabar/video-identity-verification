package com.example.videokyc.workflow.domain;

import com.example.videokyc.workflow.domain.shared.CanonicalJsonSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonSerializerTest {

    @Test
    @DisplayName("Should produce identical canonical JSON and checksum regardless of field ordering")
    void shouldProduceDeterministicJsonAndChecksum() {
        String json1 = """
                {
                  "z": 100,
                  "a": "hello",
                  "nested": {
                    "beta": false,
                    "alpha": true
                  }
                }
                """;

        String json2 = """
                {
                  "nested": {
                    "alpha": true,
                    "beta": false
                  },
                  "a": "hello",
                  "z": 100
                }
                """;

        String canonical1 = CanonicalJsonSerializer.canonicalizeJsonString(json1);
        String canonical2 = CanonicalJsonSerializer.canonicalizeJsonString(json2);

        assertThat(canonical1).isEqualTo(canonical2);
        assertThat(canonical1).isEqualTo("{\"a\":\"hello\",\"nested\":{\"alpha\":true,\"beta\":false},\"z\":100}");

        String checksum1 = CanonicalJsonSerializer.computeSha256Checksum(canonical1);
        String checksum2 = CanonicalJsonSerializer.computeSha256Checksum(canonical2);

        assertThat(checksum1).isEqualTo(checksum2);
        assertThat(checksum1).hasSize(64);
        assertThat(checksum1).matches("^[a-f0-9]{64}$");
    }
}
