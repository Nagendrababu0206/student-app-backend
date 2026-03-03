package com.eduai.backend.model;

public record ChatRequest(
        String message,
        String scope,
        AssessmentSnapshot latestAssessment
) {}
