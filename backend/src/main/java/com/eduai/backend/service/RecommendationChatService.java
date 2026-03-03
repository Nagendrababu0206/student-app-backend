package com.eduai.backend.service;

import com.eduai.backend.model.AssessmentSnapshot;
import com.eduai.backend.model.ChatRequest;
import com.eduai.backend.model.RecommendationScore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class RecommendationChatService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationChatService.class);
    private static final String SYSTEM_PROMPT =
            "You are EDUAI assistant for school students only. " +
            "Give concise, practical study guidance and recommendations. " +
            "If user asks outside school scope, politely refuse and redirect to school-level guidance. " +
            "When user asks for a specific subject, recommend only courses for that subject.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public String generateReply(ChatRequest request) {
        String userMessage = request.message() == null ? "" : request.message().trim();
        if (userMessage.isEmpty()) {
            return "Please type your question. I can suggest school-level learning paths.";
        }

        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (!"school_students_only".equalsIgnoreCase(request.scope())) {
            return "This chatbot is configured for school students only. Please use school-level queries.";
        }

        if (lower.contains("last recommendation") && request.latestAssessment() != null) {
            return formatLastRecommendation(request.latestAssessment());
        }

        String deepSeekReply = fetchDeepSeekReply(request);
        if (deepSeekReply != null && !deepSeekReply.isBlank()) {
            return deepSeekReply.trim();
        }

        // Fallback to local deterministic logic when DeepSeek is unavailable.
        StudentProfile profile = parseProfile(lower);
        List<String> ranked = rankRecommendations(profile);

        String top = ranked.isEmpty() ? "Foundations of Algebra" : ranked.get(0);
        String second = ranked.size() > 1 ? ranked.get(1) : "Concept Videos and Visual Notes";
        return "For school students, I recommend " + top + " and " + second + ". Intent detected: " + profile.intent + ".";
    }

    private String fetchDeepSeekReply(ChatRequest request) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DeepSeek disabled: DEEPSEEK_API_KEY is missing.");
            return null;
        }

        String configuredEndpoint = System.getenv("DEEPSEEK_ENDPOINT");
        List<String> endpointCandidates = configuredEndpoint == null || configuredEndpoint.isBlank()
                ? List.of(
                        "https://api.deepseek.com/chat/completions",
                        "https://api.deepseek.com/v1/chat/completions"
                )
                : Stream.of(
                                configuredEndpoint,
                                configuredEndpoint.replace("/v1/chat/completions", "/chat/completions"),
                                configuredEndpoint.replace("/chat/completions", "/v1/chat/completions")
                        )
                        .distinct()
                        .toList();

        String model = System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-chat");
        String userPrompt = buildUserPrompt(request);

        DeepSeekRequest payload = new DeepSeekRequest(
                model,
                List.of(
                        new DeepSeekMessage("system", SYSTEM_PROMPT),
                        new DeepSeekMessage("user", userPrompt)
                ),
                0.4
        );

        for (String endpoint : endpointCandidates) {
            try {
                String body = objectMapper.writeValueAsString(payload);
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("DeepSeek call failed: endpoint={} status={} body={}",
                            endpoint,
                            response.statusCode(),
                            abbreviate(response.body(), 300));
                    continue;
                }

                DeepSeekResponse parsed = objectMapper.readValue(response.body(), DeepSeekResponse.class);
                if (parsed == null || parsed.choices == null || parsed.choices.isEmpty()) {
                    log.warn("DeepSeek response had no choices: endpoint={}", endpoint);
                    continue;
                }

                DeepSeekChoice choice = parsed.choices.get(0);
                if (choice == null || choice.message == null) {
                    log.warn("DeepSeek response missing message in first choice: endpoint={}", endpoint);
                    continue;
                }
                return choice.message.content;
            } catch (IOException ex) {
                log.warn("DeepSeek IO error for endpoint={}: {}", endpoint, ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("DeepSeek request interrupted for endpoint={}", endpoint);
                return null;
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid DeepSeek endpoint configured: {}", endpoint);
            }
        }
        return null;
    }

    private String abbreviate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    private String buildUserPrompt(ChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User message: ").append(request.message() == null ? "" : request.message().trim()).append("\n");
        prompt.append("Scope: ").append(request.scope() == null ? "unknown" : request.scope()).append("\n");
        prompt.append("If scope is not school_students_only, reject politely.\n");

        if (request.latestAssessment() != null) {
            AssessmentSnapshot snapshot = request.latestAssessment();
            prompt.append("Latest assessment:\n");
            prompt.append("- Grade: ").append(snapshot.grade()).append("\n");
            prompt.append("- Subject: ").append(snapshot.subject()).append("\n");
            prompt.append("- Style: ").append(snapshot.style()).append("\n");
            prompt.append("- Performance: ").append(snapshot.performance()).append("\n");
            prompt.append("- Quiz: ").append(snapshot.quizScore()).append("\n");
            prompt.append("- Intent: ").append(snapshot.intent()).append("\n");
        }
        StudentProfile profile = parseProfile((request.message() == null ? "" : request.message()).toLowerCase(Locale.ROOT));
        if (profile.subjectExplicit()) {
            prompt.append("User explicitly requested subject: ").append(profile.subject()).append("\n");
            prompt.append("Return only ").append(profile.subject()).append(" course suggestions.\n");
        }
        return prompt.toString();
    }

    private String formatLastRecommendation(AssessmentSnapshot snapshot) {
        List<RecommendationScore> list = snapshot.recommendations() == null ? List.of() : snapshot.recommendations();
        if (list.isEmpty()) {
            return "No previous recommendations found. Please run a new assessment first.";
        }

        String first = list.get(0).name();
        String second = list.size() > 1 ? list.get(1).name() : "Concept Videos and Visual Notes";
        return "Your last school recommendation was: " + first + " and " + second + ".";
    }

    private StudentProfile parseProfile(String text) {
        String subject = "mathematics";
        if (text.contains("english") || text.contains("writing") || text.contains("comprehension")) {
            subject = "english";
        } else if (text.contains("science") || text.contains("biology")) {
            subject = "science";
        } else if (text.contains("physics")) {
            subject = "physics";
        } else if (text.contains("chemistry")) {
            subject = "chemistry";
        } else if (text.contains("social") || text.contains("peer") || text.contains("group study")) {
            subject = "social";
        } else if (text.contains("program")) {
            subject = "programming";
        } else if (text.contains("analytic") || text.contains("data")) {
            subject = "analytics";
        } else if (text.contains("ai") || text.contains("machine learning")) {
            subject = "ai";
        }

        String style = "mixed";
        if (text.contains("visual")) {
            style = "visual";
        } else if (text.contains("read")) {
            style = "reading";
        } else if (text.contains("hands")) {
            style = "handson";
        }

        String performance = "medium";
        if (text.contains("low") || text.contains("weak")) {
            performance = "low";
        } else if (text.contains("high") || text.contains("strong")) {
            performance = "high";
        }

        int quiz = extractQuizScore(text);
        String intent = detectIntent(text, performance, quiz);
        return new StudentProfile(subject, subjectExplicit(text), style, performance, quiz, intent);
    }

    private boolean subjectExplicit(String text) {
        return text.contains("math")
                || text.contains("algebra")
                || text.contains("program")
                || text.contains("analytic")
                || text.contains("data")
                || text.contains("ai")
                || text.contains("machine learning")
                || text.contains("english")
                || text.contains("science")
                || text.contains("social")
                || text.contains("physics")
                || text.contains("chemistry")
                || text.contains("writing")
                || text.contains("comprehension")
                || text.contains("biology");
    }

    private int extractQuizScore(String text) {
        String[] tokens = text.split("[^0-9]+", -1);
        for (String token : tokens) {
            if (!token.isBlank()) {
                int val = Integer.parseInt(token);
                if (val >= 0 && val <= 100) {
                    return val;
                }
            }
        }
        return 65;
    }

    private String detectIntent(String text, String performance, int quizScore) {
        if (text.contains("certification") || text.contains("exam")) {
            return "Certification preparation";
        }
        if ("low".equals(performance) || quizScore < 60 || text.contains("improve") || text.contains("weak")) {
            return "Skill assessment";
        }
        if (text.contains("explore") || text.contains("topic")) {
            return "Topic exploration";
        }
        return "Structured upskilling plan";
    }

    private List<String> rankRecommendations(StudentProfile profile) {
        if (profile.subjectExplicit()) {
            return getSubjectCourses(profile.subject());
        }

        List<String> recs = new ArrayList<>();

        switch (profile.subject) {
            case "programming" -> {
                recs.add("Programming Fundamentals");
                recs.add("Data Structures with Practice");
            }
            case "analytics" -> {
                recs.add("Statistics Basics");
                recs.add("Data Visualization Studio");
            }
            case "ai" -> {
                recs.add("AI Foundations");
                recs.add("Machine Learning Concepts");
            }
            default -> {
                recs.add("Foundations of Algebra");
                recs.add("Applied Problem Solving Lab");
            }
        }

        if ("Certification preparation".equals(profile.intent)) {
            recs.add("Certification Mock Test Series");
        }
        if ("Skill assessment".equals(profile.intent) || "low".equals(profile.performance) || profile.quizScore < 60) {
            recs.add("Diagnostic Quiz Pack and Gap Remediation");
            recs.add("Beginner Pace Mentor Sessions");
        }
        if ("visual".equals(profile.style)) {
            recs.add("Concept Videos and Visual Notes");
        }
        if ("handson".equals(profile.style)) {
            recs.add("Hands-on Assignments and Weekly Projects");
        }

        return recs.stream().distinct().limit(5).toList();
    }

    private List<String> getSubjectCourses(String subject) {
        return switch (subject) {
            case "programming" -> List.of("Programming Fundamentals", "Data Structures with Practice");
            case "analytics" -> List.of("Statistics Basics", "Data Visualization Studio");
            case "ai" -> List.of("AI Foundations", "Machine Learning Concepts");
            case "english" -> List.of("Academic Writing Skills", "Reading Comprehension Strategies");
            case "social" -> List.of("Peer Study Groups", "School Study Skills Bootcamp");
            case "science", "physics", "chemistry" -> List.of("Science Concepts Made Simple", "Probability for Beginners");
            default -> List.of("Foundations of Algebra", "Applied Problem Solving Lab");
        };
    }

    private record StudentProfile(String subject, boolean subjectExplicit, String style, String performance, int quizScore, String intent) {}

    private record DeepSeekRequest(String model, List<DeepSeekMessage> messages, double temperature) {}

    private record DeepSeekMessage(String role, String content) {}

    private static class DeepSeekResponse {
        public List<DeepSeekChoice> choices;
    }

    private static class DeepSeekChoice {
        public DeepSeekMessageContent message;
    }

    private static class DeepSeekMessageContent {
        @JsonProperty("content")
        public String content;
    }
}
