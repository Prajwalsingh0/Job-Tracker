package com.jobhunt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Final outcome of a job application. Kept separate from {@link JobStatus} so the
 * pipeline can still show rejected/withdrawn/ghosted stages.
 */
public enum JobOutcome {
    OFFER("offer"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn"),
    GHOSTED("ghosted");

    private final String value;

    JobOutcome(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobOutcome fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (JobOutcome outcome : values()) {
            if (outcome.value.equalsIgnoreCase(value) || outcome.name().equalsIgnoreCase(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Unknown job outcome: " + value);
    }
}
