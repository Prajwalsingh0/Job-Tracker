package com.jobhunt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Where the work actually happens. Null means "not specified". */
public enum WorkMode {
    REMOTE("remote"),
    HYBRID("hybrid"),
    ONSITE("onsite");

    private final String value;

    WorkMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static WorkMode fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WorkMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value) || mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown work mode: " + value);
    }
}
