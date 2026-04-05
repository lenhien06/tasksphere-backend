package com.zone.tasksphere.ai.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure-Java scoring engine for AI task assignment (Feature 2).
 *
 * Formula:
 *   skill_score      = Jaccard(taskSkills, memberSkills)               × 0.5
 *   workload_score   = clamp(1 - activeTaskCount / 5, 0, 1)           × 0.3
 *   difficulty_score = clamp(1 - |memberAvgSP - taskSP| / 10, 0, 1)  × 0.2
 *   total_score      = sum of above, rounded to 3 decimal places
 */
@Component
public class ScoringEngine {

    private static final BigDecimal SKILL_W      = new BigDecimal("0.5");
    private static final BigDecimal WORKLOAD_W   = new BigDecimal("0.3");
    private static final BigDecimal DIFFICULTY_W = new BigDecimal("0.2");

    private static final double MAX_ACTIVE = 5.0;
    private static final double MAX_SP_DIFF = 10.0;

    public record ScoreResult(
            BigDecimal skillScore,
            BigDecimal workloadScore,
            BigDecimal difficultyScore,
            BigDecimal totalScore
    ) {}

    /**
     * Score one (task, member) pair.
     *
     * @param taskSkills      skill_tags_required from task (may be null/empty)
     * @param memberSkills    skill_tags from user (may be null/empty)
     * @param activeTaskCount project_members.active_task_count
     * @param avgStoryPoints  project_members.avg_story_points (null if no history)
     * @param taskStoryPoints tasks.story_points (null if not set)
     */
    public ScoreResult score(
            List<String> taskSkills,
            List<String> memberSkills,
            int activeTaskCount,
            Double avgStoryPoints,
            Integer taskStoryPoints
    ) {
        BigDecimal skill      = computeSkill(taskSkills, memberSkills);
        BigDecimal workload   = computeWorkload(activeTaskCount);
        BigDecimal difficulty = computeDifficulty(avgStoryPoints, taskStoryPoints);

        BigDecimal total = skill.multiply(SKILL_W)
                .add(workload.multiply(WORKLOAD_W))
                .add(difficulty.multiply(DIFFICULTY_W))
                .setScale(3, RoundingMode.HALF_UP);

        return new ScoreResult(skill, workload, difficulty, total);
    }

    // ─────────────────────────────────────────────────────────────────────────

    /** Jaccard similarity, case-insensitive, trimmed. */
    private BigDecimal computeSkill(List<String> taskSkills, List<String> memberSkills) {
        if (taskSkills == null || taskSkills.isEmpty()) {
            // No skill constraints → anyone qualifies
            return bd(1.0);
        }
        if (memberSkills == null || memberSkills.isEmpty()) {
            return bd(0.0);
        }

        Set<String> taskSet   = normalize(taskSkills);
        Set<String> memberSet = normalize(memberSkills);

        Set<String> intersection = new HashSet<>(taskSet);
        intersection.retainAll(memberSet);

        Set<String> union = new HashSet<>(taskSet);
        union.addAll(memberSet);

        if (union.isEmpty()) return bd(0.0);

        double jaccard = (double) intersection.size() / union.size();
        return bd(jaccard);
    }

    /** 1 − (activeTasks / 5), clamped to [0, 1]. */
    private BigDecimal computeWorkload(int activeTaskCount) {
        double raw     = 1.0 - (activeTaskCount / MAX_ACTIVE);
        double clamped = Math.max(0.0, Math.min(1.0, raw));
        return bd(clamped);
    }

    /** 1 − |avgSP − taskSP| / 10, clamped to [0, 1]. Neutral 0.5 when data absent. */
    private BigDecimal computeDifficulty(Double avgStoryPoints, Integer taskStoryPoints) {
        if (avgStoryPoints == null || taskStoryPoints == null) {
            return new BigDecimal("0.500"); // neutral – no velocity data yet
        }
        double diff    = Math.abs(avgStoryPoints - taskStoryPoints);
        double raw     = 1.0 - (diff / MAX_SP_DIFF);
        double clamped = Math.max(0.0, Math.min(1.0, raw));
        return bd(clamped);
    }

    private Set<String> normalize(List<String> tags) {
        Set<String> result = new HashSet<>();
        for (String tag : tags) {
            if (tag != null && !tag.isBlank()) {
                result.add(tag.trim().toLowerCase());
            }
        }
        return result;
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
