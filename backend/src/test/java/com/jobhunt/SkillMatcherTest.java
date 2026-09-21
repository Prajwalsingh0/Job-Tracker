package com.jobhunt;

import com.jobhunt.ai.SkillMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matcher is deterministic, so it is unit tested directly with no Spring context.
 */
class SkillMatcherTest {

    @Test
    void extractsKnownSkillsAndIgnoresLookalikes() {
        var skills = SkillMatcher.extractSkills(
                "We use Java and JavaScript. Our stack is Spring Boot with PostgreSQL and Docker.");

        assertTrue(skills.contains("Java"), "Java should be detected");
        assertTrue(skills.contains("JavaScript"), "JavaScript should be detected");
        assertTrue(skills.contains("Spring Boot"), "Spring Boot should be detected");
        assertTrue(skills.contains("PostgreSQL"), "PostgreSQL should be detected");
        assertTrue(skills.contains("Docker"), "Docker should be detected");
    }

    @Test
    void wordBoundariesPreventPartialMatches() {
        // "Java" must not be reported just because "JavaScript" appears.
        var skills = SkillMatcher.extractSkills("Only JavaScript here.");
        assertFalse(skills.contains("Java"), "Java must not match inside JavaScript");

        // "React" must not match inside "reactive".
        var reactive = SkillMatcher.extractSkills("Building reactive systems.");
        assertFalse(reactive.contains("React"), "React must not match inside reactive");
    }

    @Test
    void scoresCoverageOfTheJobsSkills() {
        String jd = "Senior Java engineer. Required: Java, Spring Boot, PostgreSQL, Kafka, Kubernetes.";
        String resume = "I have built services in Java with Spring Boot and PostgreSQL.";

        SkillMatcher.Result result = SkillMatcher.match(jd, resume);

        assertTrue(result.jobSkills().contains("Kafka"), "Kafka is named in the posting");
        assertTrue(result.matchedSkills().contains("Java"));
        assertTrue(result.matchedSkills().contains("PostgreSQL"));
        assertTrue(result.missingSkills().contains("Kafka"));
        assertTrue(result.missingSkills().contains("Kubernetes"));

        // Three of the five recognised skills are covered.
        assertEquals(60, result.skillCoverage());
        assertTrue(result.keywordOverlap() > 0);
    }

    @Test
    void aPerfectOverlapScoresOneHundred() {
        String text = "Java, Spring Boot, PostgreSQL";
        SkillMatcher.Result result = SkillMatcher.match(text, text);

        assertEquals(100, result.skillCoverage());
        assertEquals(100, result.keywordOverlap());
        assertTrue(result.missingSkills().isEmpty());
    }

    @Test
    void handlesEmptyAndNullInputsWithoutFailing() {
        SkillMatcher.Result nulls = SkillMatcher.match(null, null);
        assertEquals(0, nulls.skillCoverage());
        assertEquals(List.of(), nulls.jobSkills());

        SkillMatcher.Result empty = SkillMatcher.match("", "Java");
        assertEquals(0, empty.skillCoverage());
        assertTrue(empty.matchedSkills().isEmpty());
    }

    @Test
    void isCaseInsensitive() {
        SkillMatcher.Result result = SkillMatcher.match("EXPERIENCE WITH JAVA AND DOCKER", "java / docker");
        assertTrue(result.matchedSkills().contains("Java"));
        assertTrue(result.matchedSkills().contains("Docker"));
    }
}
