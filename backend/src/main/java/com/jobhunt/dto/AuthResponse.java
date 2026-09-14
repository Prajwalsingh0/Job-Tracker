package com.jobhunt.dto;

public record AuthResponse(String token, String tokenType, UserDto user) {

    public static AuthResponse of(String token, UserDto user) {
        return new AuthResponse(token, "Bearer", user);
    }
}
