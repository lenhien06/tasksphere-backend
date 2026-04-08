package com.zone.tasksphere.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SkillTaxonomy {

    public enum Capability {
        DEVELOPMENT,
        TESTING
    }

    private static final List<String> TESTING_ALIASES = List.of(
            "qa",
            "qc",
            "tester",
            "testing",
            "quality assurance",
            "test engineer",
            "manual tester",
            "manual testing",
            "automation tester",
            "automation testing"
    );

    private static final List<String> DEVELOPMENT_ALIASES = List.of(
            "dev",
            "developer",
            "software developer",
            "software engineer",
            "frontend developer",
            "backend developer",
            "fullstack developer",
            "full-stack developer",
            "web developer",
            "mobile developer"
    );

    private SkillTaxonomy() {
    }

    public static List<String> canonicalizeSkillTags(List<String> rawSkillTags) {
        if (rawSkillTags == null || rawSkillTags.isEmpty()) {
            return List.of();
        }

        Set<String> canonical = new LinkedHashSet<>();
        for (String raw : rawSkillTags) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            String normalized = normalize(trimmed);
            if (matchesAny(normalized, TESTING_ALIASES)) {
                canonical.add("Testing");
                continue;
            }
            if (matchesAny(normalized, DEVELOPMENT_ALIASES)) {
                canonical.add("Development");
                continue;
            }
            canonical.add(trimmed);
        }

        return new ArrayList<>(canonical);
    }

    public static boolean hasCapability(List<String> skillTags, Capability capability) {
        if (skillTags == null || skillTags.isEmpty()) {
            return false;
        }
        return switch (capability) {
            case TESTING -> canonicalizeSkillTags(skillTags).stream()
                    .map(SkillTaxonomy::normalize)
                    .anyMatch(tag -> tag.equals("testing"));
            case DEVELOPMENT -> canonicalizeSkillTags(skillTags).stream()
                    .map(SkillTaxonomy::normalize)
                    .anyMatch(tag -> tag.equals("development"));
        };
    }

    private static boolean matchesAny(String normalizedSkill, List<String> aliases) {
        return aliases.stream().anyMatch(alias -> normalizedSkill.contains(alias));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
