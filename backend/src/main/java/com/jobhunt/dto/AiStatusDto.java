package com.jobhunt.dto;

/** Whether generated content is available, and what to do about it if not. */
public record AiStatusDto(String provider, boolean available, String hint) {
}
