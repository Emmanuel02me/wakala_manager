package com.Wakala.v1.Dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        String role,
        boolean active,
        LocalDateTime deletedAt) {
}