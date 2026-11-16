package com.example.videokyc.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/** Dedicated service identity is injected only on the server; never sent to the browser. */
@Component
public class HttpDmsGateway implements DmsGateway {
    private static final int MAX_VIDEO_BYTES = 50 * 1024 * 1024;
    private final RestClient client;
    private final String password;
    private final String username;

    public HttpDmsGateway(RestClient.Builder builder,
                          @Value("${auth.dms.base-url}") String baseUrl,
                          @Value("${auth.dms.username}") String username,
                          @Value("${auth.dms.password:}") String password) {
        URI uri = URI.create(baseUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme()) &&
                !("http".equalsIgnoreCase(uri.getScheme()) &&
                  ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())))) {
            throw new IllegalArgumentException("Remote DMS credentials require HTTPS");
        }
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.username = username;
        this.password = password;
    }

    private void checkConfigured() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new VerificationException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "DMS service credentials are not configured");
        }
    }

    @Override
    public UUID uploadUserVideo(UUID userId, MultipartFile video) {
        checkConfigured();
        if (video == null || video.isEmpty()) throw new VerificationException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Video is required");
        if (video.getSize() > MAX_VIDEO_BYTES) throw new VerificationException(
                org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "Video must be at most 50 MiB");
        String name = video.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).matches("[^/\\\\]{1,150}\\.(mp4|webm)")) {
            throw new VerificationException(org.springframework.http.HttpStatus.BAD_REQUEST, "Only MP4 and WebM are accepted");
        }
        try {
            byte[] bytes = video.getBytes();
            ByteArrayResource resource = new ByteArrayResource(bytes) {
                @Override public String getFilename() { return name; }
            };
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("subsystem", "AUTH");
            parts.add("ownerType", "USER");
            parts.add("ownerReference", userId.toString());
            parts.add("file", resource);
            UploadResult response = client.post().uri("/api/v1/dms/files")
                    .headers(this::authorizedHeaders)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts).retrieve().body(UploadResult.class);
            if (response == null || response.id() == null ||
                    !"AUTH".equals(response.subsystem()) || !"USER".equals(response.ownerType()) ||
                    !userId.toString().equals(response.ownerReference()) ||
                    !isVideo(response.contentType())) {
                throw new VerificationException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "DMS returned unexpected video metadata");
            }
            return response.id();
        } catch (IOException | RestClientException error) {
            throw new VerificationException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Video storage is temporarily unavailable");
        }
    }

    @Override
    public VideoContent downloadVideo(UUID fileId) {
        checkConfigured();
        try {
            var response = client.get().uri("/api/v1/dms/files/{id}/content", fileId)
                    .headers(this::authorizedHeaders)
                    .retrieve().toEntity(byte[].class);
            byte[] bytes = response.getBody();
            String type = response.getHeaders().getContentType() == null ? "" :
                    response.getHeaders().getContentType().toString();
            if (bytes == null || bytes.length > MAX_VIDEO_BYTES || !isVideo(type)) {
                throw new VerificationException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "DMS returned invalid video content");
            }
            return new VideoContent(bytes, type);
        } catch (RestClientException error) {
            throw new VerificationException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Video retrieval is temporarily unavailable");
        }
    }

    @Override
    public void cleanup(UUID fileId) {
        checkConfigured();
        try {
            client.delete().uri("/api/v1/dms/files/{id}", fileId)
                    .headers(this::authorizedHeaders).retrieve().toBodilessEntity();
            client.post().uri("/api/v1/dms/files/{id}/purge", fileId)
                    .headers(this::authorizedHeaders).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new VerificationException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "DMS compensation could not complete");
        }
    }

    private void authorizedHeaders(HttpHeaders headers) {
        headers.setBasicAuth(username, password);
        String requestId = MDC.get("requestId");
        if (requestId != null && requestId.length() <= 64 &&
                requestId.matches("[A-Za-z0-9._-]{1,64}")) {
            headers.set("X-Request-ID", requestId);
        }
    }

    private static boolean isVideo(String type) {
        return type != null && (type.equalsIgnoreCase("video/mp4") ||
                type.equalsIgnoreCase("video/webm"));
    }

    public record UploadResult(UUID id, String subsystem, String ownerType,
                                String ownerReference, String contentType) { }
}
