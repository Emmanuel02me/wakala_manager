package com.Wakala.v1.Dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username inahitajika") String username,
        @NotBlank(message = "Password inahitajika") String password
) {}