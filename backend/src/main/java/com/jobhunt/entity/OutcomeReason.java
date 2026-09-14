package com.jobhunt.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Reason attached to a negative application outcome. */
public enum OutcomeReason {
    POSITION_FILLED("position_filled"),
    NOT_QUALIFIED("not_qualified"),
    CULTURE_FIT("culture_fit"),
    SALARY_MISMATCH("salary_mismatch"),
    OTHER("other");

    private final String value;

    OutcomeReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OutcomeReason fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OutcomeReason reason : values()) {
            if (reason.value.equalsIgnoreCase(value) || reason.name().equalsIgnoreCase(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown outcome reason: " + value);
    }
}
