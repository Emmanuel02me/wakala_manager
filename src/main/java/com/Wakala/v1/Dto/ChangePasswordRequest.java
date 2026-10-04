package com.Wakala.v1.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Password ya sasa inahitajika") String currentPassword,
        @NotBlank(message = "Password mpya inahitajika") @Size(min = 6, message = "Password iwe angalau herufi 6") String newPassword) {
}