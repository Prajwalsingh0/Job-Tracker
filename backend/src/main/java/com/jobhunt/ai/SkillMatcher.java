package com.jobhunt.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, explainable matching between a job description and a candidate's own text.
 *
 * <p>There is no model involved and no inference: a skill counts as present only when the
 * exact word appears in the text the user provided. That is why the result is reported as
 * keyword overlap with a written explanation rather than as an "ATS score" — claiming the
 * latter would misrepresent what this actually does.
 */
public final class SkillMatcher {

    private SkillMatcher() {
    }

    public static Set<String> extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }

        // Pad so \b behaves at the string edges.
        String haystack = " " + text.toLowerCase(Locale.ROOT) + " ";

        for (String skill : SkillVocabulary.all()) {
            if (Pattern.compile("\\b" + Pattern.quote(skill.toLowerCase(Locale.ROOT)) + "\\b")
                    .matcher(haystack)
                    .find()) {
                found.add(skill);
            }
        }

        return dropSubsumedSkills(found);
    }

    /**
     * Removes a skill when a longer matched skill is simply that skill plus a word.
     *
     * <p>"Spring Boot" and "Spring" both match the phrase "Spring Boot", which would count
     * one mention twice and distort the coverage figure. "Java" is deliberately not dropped
     * when "JavaScript" matches, because the longer phrase does not begin with "Java ".
     */
    private static Set<String> dropSubsumedSkills(Set<String> skills) {
        List<String> longestFirst = skills.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        Set<String> kept = new LinkedHashSet<>(longestFirst);
        for (String longer : longestFirst) {
            for (String shorter : longestFirst) {
                if (longer.length() > shorter.length()
                        && longer.toLowerCase(Locale.ROOT)
                                .startsWith(shorter.toLowerCase(Locale.ROOT) + " ")) {
                    kept.remove(shorter);
                }
            }
        }
        return kept;
    }

    public static Result match(String jobDescription, String resumeText) {
        Set<String> jdSkills = extractSkills(jobDescription);
        Set<String> resumeSkills = extractSkills(resumeText);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : jdSkills) {
            if (resumeSkills.contains(skill)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int skillScore = jdSkills.isEmpty()
                ? 0
                : (int) Math.round(matched.size() * 100.0 / jdSkills.size());

        Set<String> jdWords = SkillVocabulary.significantWords(jobDescription);
        Set<String> resumeWords = SkillVocabulary.significantWords(resumeText);
        long sharedWords = jdWords.stream().filter(resumeWords::contains).count();
        int wordScore = jdWords.isEmpty() ? 0 : (int) Math.round(sharedWords * 100.0 / jdWords.size());

        return new Result(skillScore, wordScore, List.copyOf(jdSkills), matched, missing);
    }

    public record Result(
            int skillCoverage,
            int keywordOverlap,
            List<String> jobSkills,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {
    }
}
