package com.jobhunt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Supported resume document formats. */
public enum ResumeFileType {
    PDF("pdf"),
    DOCX("docx");

    private final String value;

    ResumeFileType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ResumeFileType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResumeFileType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown resume file type: " + value);
    }
}
