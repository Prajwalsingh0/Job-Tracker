package com.jobhunt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Job pipeline status. Serialized to JSON using the lowercase wire values that the
 * React frontend already uses (e.g. {@code phone_screen}), while the database stores
 * the uppercase enum name.
 */
public enum JobStatus {
    WISHLIST("wishlist"),
    APPLIED("applied"),
    PHONE_SCREEN("phone_screen"),
    INTERVIEW("interview"),
    OFFER("offer"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn"),
    GHOSTED("ghosted");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static JobStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (JobStatus status : values()) {
            if (status.value.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown job status: " + value);
    }
}
