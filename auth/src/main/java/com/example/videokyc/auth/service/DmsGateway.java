package com.example.videokyc.auth.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

/** Service boundary: never import DMS classes into AUTH. */
public interface DmsGateway {
    UUID uploadUserVideo(UUID userId, MultipartFile video);
    VideoContent downloadVideo(UUID fileId);
    void cleanup(UUID fileId);
    record VideoContent(byte[] bytes, String contentType) { }
}
