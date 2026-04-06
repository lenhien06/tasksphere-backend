package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response from analyze-description: what AI understood + questions to fill gaps.
 */
@Data
@Builder
public class AnalyzeDescriptionResponse {

    private UnderstoodFields understood;
    private List<String> missingFields;
    private List<AiQuestion> questions;

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class UnderstoodFields {
        private String projectName;
        private String projectType;
        private List<String> features;
    }

    @Data
    @Builder
    public static class AiQuestion {
        /** Field this question addresses: techStack | deadline | teamMembers */
        private String field;
        private String question;
        /** null for member-select type */
        private List<QuestionOption> options;
        /** "member-select" for team selection, null for single-choice */
        private String type;
        private List<MemberOption> members;
        private boolean allowCustom;
        private String customPlaceholder;
    }

    @Data
    @Builder
    public static class QuestionOption {
        private String label;
        private String value;
    }

    @Data
    @Builder
    public static class MemberOption {
        private String userId;
        private String fullName;
        private String avatarUrl;
        private List<String> skillTags;
    }
}
