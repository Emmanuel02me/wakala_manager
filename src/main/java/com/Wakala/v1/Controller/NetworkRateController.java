package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.NetworkRateResponse;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Service.NetworkRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network-rates")
@RequiredArgsConstructor
public class NetworkRateController {

    private final NetworkRateService networkRateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<NetworkRateResponse> getAll() {
        return networkRateService.getAllActive();
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<NetworkRateResponse> getByProvider(
            @PathVariable Long providerId,
            @RequestParam Transaction.TransactionType type) {
        return networkRateService.getByProviderAndType(providerId, type);
    }
}