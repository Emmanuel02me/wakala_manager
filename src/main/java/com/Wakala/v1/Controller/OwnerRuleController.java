package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.NetworkRateResponse;
import com.Wakala.v1.Dto.OwnerRuleRequest;
import com.Wakala.v1.Dto.OwnerRuleResponse;
import com.Wakala.v1.Dto.UpdateOwnerRuleRequest;
import com.Wakala.v1.Entity.NetworkRate;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.NetworkRateService;
import com.Wakala.v1.Service.OwnerRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner-rules")
@RequiredArgsConstructor
public class OwnerRuleController {

    private final OwnerRuleService ruleService;
    private final NetworkRateService networkRateService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerRuleResponse> create(
            @Valid @RequestBody OwnerRuleRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(req, currentUser.getId()));
    }

    @PostMapping("/supersede")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerRuleResponse> supersede(
            @Valid @RequestBody UpdateOwnerRuleRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(ruleService.supersede(req, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @CurrentUser User currentUser) {
        ruleService.deactivate(id, currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<OwnerRuleResponse> getAll() {
        return ruleService.getAllActive();
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<OwnerRuleResponse> getByProvider(
            @PathVariable Long providerId,
            @RequestParam Transaction.TransactionType type) {
        return ruleService.getByProviderAndType(providerId, type);
    }

    @GetMapping("/preview-network")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<NetworkRateResponse> previewNetworkRate(
            @RequestParam Long providerId,
            @RequestParam Transaction.TransactionType type,
            @RequestParam BigDecimal amount) {
        try {
            NetworkRate rate = networkRateService.findApplicableRate(providerId, type, amount);
            NetworkRateResponse response = new NetworkRateResponse(
                    rate.getId(),
                    rate.getProvider().getId(),
                    rate.getProvider().getName(),
                    rate.getTransactionType().name(),
                    rate.getMinAmount(),
                    rate.getMaxAmount(),
                    rate.getNetworkCommission(),
                    rate.getEffectiveFrom(),
                    rate.getEffectiveTo(),
                    rate.isActive(),
                    rate.isEffectiveOn(LocalDate.now()),
                    rate.getNotes());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}