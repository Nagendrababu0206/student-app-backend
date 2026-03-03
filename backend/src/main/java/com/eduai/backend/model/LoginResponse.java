package com.eduai.backend.model;

public record LoginResponse(
        boolean success,
        String message,
        String user
) {}
