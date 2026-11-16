package com.example.videokyc.dms.domain;

/** PURGE_PENDING supports retrying filesystem/DB consistency after a failure. */
public enum FileStatus {
    ACTIVE,
    DELETED,
    PURGE_PENDING,
    PURGED
}
