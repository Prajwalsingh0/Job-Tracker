package com.jobhunt.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

/** The kinds of text the assistant can generate for a job. */
public enum AiTask {
    JOB_SUMMARY,
    INTERVIEW_QUESTIONS,
    COVER_LETTER,
    STAR_PRACTICE,
    LEARNING_PLAN;

    @JsonCreator
    public static AiTask fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AiTask task : values()) {
            if (task.name().equalsIgnoreCase(value)) {
                return task;
            }
        }
        throw new IllegalArgumentException("Unknown AI task: " + value);
    }
}
