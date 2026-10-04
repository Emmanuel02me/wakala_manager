package com.Wakala.v1.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank(message = "Jina kamili linahitajika") @Size(max = 100) String fullName,

        @Size(max = 15, message = "Namba ya simu iwe kati ya tarakimu 10 na 15") String phone) {
}