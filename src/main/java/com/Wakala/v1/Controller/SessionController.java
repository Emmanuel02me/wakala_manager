package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<SessionResponse> open(
            @Valid @RequestBody OpenSessionRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(sessionService.openSession(req, currentUser));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ResponseEntity<ReconciliationResponse> close(
            @PathVariable Long id,
            @Valid @RequestBody CloseSessionRequest req,
            @CurrentUser User currentUser) {
        return ResponseEntity.ok(sessionService.closeSession(id, req, currentUser));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public List<SessionResponse> getAll() {
        return sessionService.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public SessionResponse getById(@PathVariable Long id) {
        return sessionService.getById(id);
    }

    @GetMapping("/last-closed")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public LastClosedSessionResponse getLastClosed() {
        return sessionService.getLastClosedSession();
    }
}