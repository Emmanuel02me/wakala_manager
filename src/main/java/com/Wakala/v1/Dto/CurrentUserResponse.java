package com.Wakala.v1.Dto;

public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        String role
) {}