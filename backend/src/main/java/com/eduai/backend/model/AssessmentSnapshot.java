package com.eduai.backend.model;

import java.util.List;

public record AssessmentSnapshot(
        String grade,
        String subject,
        String style,
        String performance,
        Integer quizScore,
        String intent,
        List<RecommendationScore> recommendations
) {}
