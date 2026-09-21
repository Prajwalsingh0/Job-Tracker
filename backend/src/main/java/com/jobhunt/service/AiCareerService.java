package com.jobhunt.service;

import com.jobhunt.ai.AiProvider;
import com.jobhunt.ai.SkillMatcher;
import com.jobhunt.dto.AiStatusDto;
import com.jobhunt.dto.AiTask;
import com.jobhunt.dto.GenerateRequest;
import com.jobhunt.dto.GenerateResponse;
import com.jobhunt.dto.MatchRequest;
import com.jobhunt.dto.MatchResponse;
import com.jobhunt.entity.Job;
import com.jobhunt.exception.AiUnavailableException;
import com.jobhunt.exception.ResourceNotFoundException;
import com.jobhunt.repository.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Career-assistant features.
 *
 * <p>Matching is deterministic and always available. Everything else needs a configured AI
 * provider, and says so plainly instead of fabricating a result.
 *
 * <p>Every prompt is built only from data the user already owns — the job they saved and
 * text they supplied. Nothing about the candidate is invented or assumed.
 */
@Service
public class AiCareerService {

    static final String DISCLAIMER =
            "This is literal keyword overlap between the two texts you supplied. It is not an ATS "
                    + "score, not a prediction of your chances, and not a judgement of your suitability.";

    private static final String SYSTEM_PROMPT = """
            You are a careful career assistant helping one candidate with their own job application.

            Rules you must follow:
            - Use ONLY the information supplied below. Never invent skills, employers, dates, \
            projects, metrics or qualifications the candidate has not stated.
            - Never claim a guaranteed ATS score, a guaranteed interview, or any certainty about outcomes.
            - If something needed is missing, say what is missing rather than filling the gap.
            - Be concrete and specific. Prefer short paragraphs and plain text over headings.
            """;

    private static final int MAX_MISSING_SKILL_SUGGESTIONS = 5;

    private final JobRepository jobRepository;
    private final AiProvider provider;

    public AiCareerService(JobRepository jobRepository, AiProvider provider) {
        this.jobRepository = jobRepository;
        this.provider = provider;
    }

    public AiStatusDto status() {
        boolean available = provider.isAvailable();
        return new AiStatusDto(
                provider.name(),
                available,
                available
                        ? null
                        : "Set AI_API_KEY (and optionally AI_PROVIDER, AI_MODEL, AI_BASE_URL) to enable "
                                + "generated content. Resume matching works without a provider.");
    }

    /** Deterministic keyword matching against the job's saved description. */
    @Transactional(readOnly = true)
    public MatchResponse match(Long userId, MatchRequest request) {
        Job job = findOwned(userId, request.jobId());

        String description = job.getDescription();
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "This job has no description saved yet, so there is nothing to match against. "
                            + "Add the job description first.");
        }

        SkillMatcher.Result result = SkillMatcher.match(description, request.resumeText());

        return new MatchResponse(
                job.getId(),
                job.getCompanyName(),
                job.getJobTitle(),
                result.skillCoverage(),
                result.keywordOverlap(),
                "keyword-overlap",
                DISCLAIMER,
                result.jobSkills(),
                result.matchedSkills(),
                result.missingSkills(),
                suggestionsFor(result.missingSkills()));
    }

    /** Provider-backed generation. Throws 503 when nothing is configured. */
    @Transactional(readOnly = true)
    public GenerateResponse generate(Long userId, GenerateRequest request) {
        Job job = findOwned(userId, request.jobId());

        if (!provider.isAvailable()) {
            throw new AiUnavailableException(provider.name());
        }

        String content = provider.complete(SYSTEM_PROMPT, buildPrompt(job, request.task(), request.resumeText()));
        return new GenerateResponse(request.task(), provider.name(), content);
    }

    /**
     * Suggestions are phrased so they cannot be read as advice to embellish: the candidate is
     * told to mention the skill only if they genuinely have it.
     */
    private List<String> suggestionsFor(List<String> missingSkills) {
        if (missingSkills.isEmpty()) {
            return List.of("Every recognised skill in the posting already appears in your text. "
                    + "Check that each one is backed by something concrete you actually did.");
        }

        List<String> suggestions = new ArrayList<>();
        for (String skill : missingSkills.stream().limit(MAX_MISSING_SKILL_SUGGESTIONS).toList()) {
            suggestions.add("The posting mentions " + skill + ". If you have genuine experience with it, "
                    + "describe it explicitly with an example. Do not add it otherwise.");
        }
        if (missingSkills.size() > MAX_MISSING_SKILL_SUGGESTIONS) {
            suggestions.add((missingSkills.size() - MAX_MISSING_SKILL_SUGGESTIONS)
                    + " further term(s) from the posting are not in your text; see the full list above.");
        }
        return suggestions;
    }

    private String buildPrompt(Job job, AiTask task, String resumeText) {
        String context = """
                JOB THE CANDIDATE SAVED
                Company: %s
                Title: %s
                Location: %s
                Description:
                %s

                CANDIDATE'S OWN TEXT
                %s
                """.formatted(
                job.getCompanyName(),
                job.getJobTitle(),
                job.getLocation() == null ? "(not recorded)" : job.getLocation(),
                job.getDescription() == null || job.getDescription().isBlank()
                        ? "(no description saved)"
                        : job.getDescription(),
                resumeText == null || resumeText.isBlank()
                        ? "(the candidate did not provide any text; do not assume anything about them)"
                        : resumeText);

        String instruction = switch (task) {
            case JOB_SUMMARY -> "Summarise what this posting is actually asking for, in 4-6 short bullets. "
                    + "Call out anything ambiguous or missing from the advert.";
            case INTERVIEW_QUESTIONS -> "List 8 interview questions this candidate is likely to be asked for "
                    + "this role, ordered from most to least likely. For each, add one line on what a strong "
                    + "answer would cover. Base them on the posting and the candidate's own text.";
            case COVER_LETTER -> "Draft a cover letter of 200-300 words. Use only the candidate's stated "
                    + "experience. Do not invent achievements, metrics or employers.";
            case STAR_PRACTICE -> "Write 4 STAR practice prompts (Situation, Task, Action, Result) drawn from "
                    + "the candidate's own text, targeting the gaps between their text and this posting. "
                    + "For each, say what evidence they should be ready to give.";
            case LEARNING_PLAN -> "List 5 concrete learning steps that would close the most important gaps "
                    + "between the candidate's text and this posting. For each, name the topic, why it matters "
                    + "for this role, and a specific way to practise it.";
        };

        return instruction + "\n\n" + context;
    }

    private Job findOwned(Long userId, Long jobId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> ResourceNotFoundException.job(jobId));
    }
}
