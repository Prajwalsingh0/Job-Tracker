package com.jobhunt.dto;

/** Public representation of a user. Never exposes the password hash. */
public record UserDto(Long id, String name, String email) {
}
