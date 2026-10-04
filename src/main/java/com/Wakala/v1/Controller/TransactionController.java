package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.TransactionRequest;
import com.Wakala.v1.Dto.TransactionResponse;
import com.Wakala.v1.Dto.UpdateTransactionRequest;
import com.Wakala.v1.Dto.VoidTransactionRequest;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<TransactionResponse> record(
            @Valid @RequestBody TransactionRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.record(req, currentUser));
    }

    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<TransactionResponse> getBySession(@PathVariable Long sessionId) {
        return transactionService.getBySession(sessionId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<TransactionResponse> updateSafe(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(
                transactionService.updateSafeFields(id, req, currentUser.getId()));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TransactionResponse> voidTransaction(
            @PathVariable Long id,
            @Valid @RequestBody VoidTransactionRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(
                transactionService.voidTransaction(id, req, currentUser.getId()));
    }
}