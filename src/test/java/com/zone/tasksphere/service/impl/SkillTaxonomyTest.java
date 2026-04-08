package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.utils.SkillTaxonomy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTaxonomyTest {

    @Test
    void canonicalizeSkillTags_normalizesTestingAndDevelopmentAliases() {
        List<String> canonical = SkillTaxonomy.canonicalizeSkillTags(List.of(
                "QA Engineer",
                "backend developer",
                "Docker",
                "Manual Testing",
                "Developer"));

        assertThat(canonical).contains("Testing", "Development", "Docker");
        assertThat(canonical).doesNotHaveDuplicates();
    }

    @Test
    void hasCapability_detectsTestingCapabilityAfterCanonicalization() {
        assertThat(SkillTaxonomy.hasCapability(List.of("QC", "API Test Engineer"),
                SkillTaxonomy.Capability.TESTING)).isTrue();
        assertThat(SkillTaxonomy.hasCapability(List.of("Backend Developer"),
                SkillTaxonomy.Capability.TESTING)).isFalse();
        assertThat(SkillTaxonomy.hasCapability(List.of("Backend Developer"),
                SkillTaxonomy.Capability.DEVELOPMENT)).isTrue();
    }
}
