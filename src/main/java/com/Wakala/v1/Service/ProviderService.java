package com.Wakala.v1.Service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.Wakala.v1.Dto.ProviderRequest;
import com.Wakala.v1.Dto.ProviderResponse;
import com.Wakala.v1.Entity.Provider;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.ProviderRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderService {

    private final ProviderRepository providerRepository;

    public Provider findById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider hapatikani: " + id));
    }

    public List<ProviderResponse> getAllActive() {
        return providerRepository.findByActiveTrue().stream()
                .map(this::toResponse).toList();
    }

    public ProviderResponse createProvider(ProviderRequest req) {
    if (providerRepository.findByName(req.name()).isPresent()) {
        throw new IllegalArgumentException("Provider tayari upo: " + req.name());
    }

    Provider provider = Provider.builder()
            .name(req.name())
            .type(req.type())
            .active(true)
            .build();
        return toResponse(providerRepository.save(provider));
    }

    private ProviderResponse toResponse(Provider p) {
        return new ProviderResponse(p.getId(), p.getName(), p.getType().name(), p.isActive());
    }
}
