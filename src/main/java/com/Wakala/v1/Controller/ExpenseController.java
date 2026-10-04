package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<ExpenseResponse> record(
            @Valid @RequestBody ExpenseRequest request,
            @CurrentUser User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.record(request, currentUser));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ExpenseResponse> approve(
            @Valid @RequestBody ApproveExpenseRequest request,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(expenseService.approve(request, currentUser.getId()));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<ExpenseResponse> getPending() {
        return expenseService.getPending();
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<ExpenseResponse> getBySession(@PathVariable Long sessionId) {
        return expenseService.getBySession(sessionId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelExpenseRequest req,
            @CurrentUser User currentUser) {
        expenseService.cancel(id, req, currentUser.getId());
        return ResponseEntity.noContent().build();
    }
}