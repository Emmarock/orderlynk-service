package com.myorderlynk.app.integration;

import com.myorderlynk.app.exception.ApiException;

import java.util.Map;

/** Shared video-upload validation (accepted MIME types → canonical file extension). */
public final class VideoUploads {

    private VideoUploads() {
    }

    private static final Map<String, String> ALLOWED = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov");

    /** Returns the canonical extension for an accepted video content type, or 400 if unsupported. */
    public static String extensionOrThrow(String contentType) {
        String ext = contentType == null ? null : ALLOWED.get(contentType.toLowerCase());
        if (ext == null) {
            throw ApiException.badRequest("Unsupported video type. Use MP4, WebM or MOV.");
        }
        return ext;
    }
}
