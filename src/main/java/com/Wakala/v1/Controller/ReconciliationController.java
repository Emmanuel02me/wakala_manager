package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.ReconciliationResponse;
import com.Wakala.v1.Dto.ShortageAnalysis;
import com.Wakala.v1.Service.ReconciliationService;
import com.Wakala.v1.Service.ShortageAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reconciliation/session/{sessionId}")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ShortageAnalysisService shortageAnalysisService;
    private final ReconciliationService reconciliationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public ReconciliationResponse getBySession(@PathVariable Long sessionId) {
        return reconciliationService.calculate(sessionId);
    }

    @GetMapping("/analysis")
    @PreAuthorize("hasRole('OWNER')")   // Analysis inaonyesha faida — OWNER pekee
    public ShortageAnalysis analyze(@PathVariable Long sessionId) {
        return shortageAnalysisService.analyze(sessionId);
    }

}