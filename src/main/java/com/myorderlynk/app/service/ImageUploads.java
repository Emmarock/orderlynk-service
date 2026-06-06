package com.myorderlynk.app.service;

import com.myorderlynk.app.exception.ApiException;

import java.util.Map;

/** Shared image-upload validation (accepted MIME types → canonical file extension). */
final class ImageUploads {

    private ImageUploads() {
    }

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");

    /** Returns the canonical extension for an accepted image content type, or 400 if unsupported. */
    static String extensionOrThrow(String contentType) {
        String ext = contentType == null ? null : ALLOWED.get(contentType.toLowerCase());
        if (ext == null) {
            throw ApiException.badRequest("Unsupported image type. Use JPEG, PNG, WebP or GIF.");
        }
        return ext;
    }
}