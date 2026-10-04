package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.CommissionRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/commission-rules")
@RequiredArgsConstructor
public class CommissionRuleController {

    private final CommissionRuleService ruleService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CommissionRuleResponse> create(
            @Valid @RequestBody CommissionRuleRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ruleService.create(req, currentUser.getId()));
    }

    @PostMapping("/supersede")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<CommissionRuleResponse> supersede(
            @Valid @RequestBody UpdateCommissionRuleRequest req,
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
    public List<CommissionRuleResponse> getAll() {
        return ruleService.getAllActive();
    }

    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<CommissionRuleResponse> getByProvider(
            @PathVariable Long providerId,
            @RequestParam Transaction.TransactionType type) {
        return ruleService.getByProviderAndType(providerId, type);
    }
}