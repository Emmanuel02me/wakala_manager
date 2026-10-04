package com.Wakala.v1.Dto;

public record UserResponse(
    Long id, String username, String fullName, String phone, String role
) {}
