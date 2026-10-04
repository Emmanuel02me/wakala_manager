package com.Wakala.v1.Dto;

public record AuthResponse(
        String token,
        String username,
        String fullName,
        String role,
        Long userId
) {}