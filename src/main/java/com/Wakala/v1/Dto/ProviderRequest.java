package com.Wakala.v1.Dto;

import com.Wakala.v1.Entity.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProviderRequest(
    @NotBlank(message = "Jina la provider linahitajika") 
    String name,
    @NotNull(message = "Type inahitajika") 
    Provider.ProviderType type
) {}