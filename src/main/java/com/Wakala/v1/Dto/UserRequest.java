package com.Wakala.v1.Dto;

import com.Wakala.v1.Entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(

        @NotBlank(message = "Username inahitajika")
        @Size(min = 3, max = 50, message = "Username iwe kati ya herufi 3 na 50")
        String username,

        @NotBlank(message = "Password inahitajika")
        @Size(min = 6, message = "Password iwe angalau herufi 6")
        String password,

        @NotBlank(message = "Jina kamili linahitajika")
        @Size(max = 100)
        String fullName,

        @Size(max = 15, message = "Namba ya simu iwe kati ya tarakimu 10 na 15")
        String phone,

        @NotNull(message = "Role inahitajika")
        User.Role role
) {}