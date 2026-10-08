package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.*;
import com.Wakala.v1.Entity.NetworkRate;
import com.Wakala.v1.Entity.Provider;
import com.Wakala.v1.Entity.Transaction;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.*;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebController {
    private final ExpenseService expenseService;

    private final ShortageAnalysisService shortageAnalysisService;
    private final SessionService sessionService;
    private final TransactionService transactionService;
    private final ReconciliationService reconciliationService;
    private final ProviderService providerService;
    private final UserService userService;
    private final CommissionRuleService commissionRuleService;
    private final OwnerRuleService ownerRuleService;
    private final NetworkRateService networkRateService;

    // ═══════════════════════════════════════
    // DASHBOARD
    // ═══════════════════════════════════════
    @GetMapping({ "/", "/dashboard" })
    public String dashboard(@CurrentUser User user, Model model) {
        if (user == null)
            return "redirect:/login";

        model.addAttribute("user", user);

        SessionResponse todaySession = sessionService.getTodaySession();
        model.addAttribute("todaySession", todaySession);

        if (todaySession != null) {
            // Kikao cha leo kipo
            ReconciliationResponse recon = reconciliationService.calculate(todaySession.id());
            model.addAttribute("reconciliation", recon);
        } else {
            // Hakuna kikao cha leo — onyesha last closed
            LastClosedSessionResponse lastClosed = sessionService.getLastClosedSession();
            if (lastClosed.exists()) {
                model.addAttribute("lastClosed", lastClosed);
                ReconciliationResponse lastRecon = reconciliationService.calculate(lastClosed.sessionId());
                model.addAttribute("lastReconciliation", lastRecon);
            }
        }

        // Onyesha sessions 5 za hivi karibuni (kwa wote)
        model.addAttribute("recentSessions", sessionService.getAll().stream().limit(5).toList());

        return "dashboard";
    }

    // ═══════════════════════════════════════
    // TRANSACTIONS
    // ═══════════════════════════════════════
    @GetMapping("/web/transactions")
    public String transactions(@CurrentUser User user, Model model) {
        model.addAttribute("user", user);

        SessionResponse todaySession = sessionService.getTodaySession();
        model.addAttribute("todaySession", todaySession);

        if (todaySession != null) {
            model.addAttribute("transactions",
                    transactionService.getBySession(todaySession.id()));
        }

        model.addAttribute("providers", providerService.getAllActive());
        model.addAttribute("transactionTypes", Transaction.TransactionType.values());
        model.addAttribute("transactionForm", new TransactionForm());

        return "transactions";
    }

    @PostMapping("/web/transactions")
    public String recordTransaction(@ModelAttribute TransactionForm form,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            SessionResponse todaySession = sessionService.getTodaySession();
            if (todaySession == null) {
                ra.addFlashAttribute("error", "Hakuna kikao wazi. Fungua kikao kwanza.");
                return "redirect:/web/transactions";
            }

            TransactionRequest req = new TransactionRequest(
                    todaySession.id(),
                    form.getProviderId(),
                    form.getDestinationProviderId(),
                    form.getTransactionType(),
                    form.getAmount(),
                    form.getHasNetworkFee(),
                    form.getChargeOwnerCommission(),
                    form.getCustomerPhone(),
                    form.getCustomerName(),
                    form.getReference());

            transactionService.record(req, user);
            ra.addFlashAttribute("success", "Muamala umerekodiwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/transactions";
    }

    // ═══════════════════════════════════════
    // SESSIONS
    // ═══════════════════════════════════════
    @GetMapping("/web/sessions")
    public String sessions(@CurrentUser User user, Model model) {
        model.addAttribute("user", user);

        List<SessionResponse> allSessions = sessionService.getAll();
        model.addAttribute("sessions", allSessions);
        model.addAttribute("todaySession", sessionService.getTodaySession());
        model.addAttribute("providers", providerService.getAllActive());

        if (user.getRole() == User.Role.OWNER) {
            long totalSessions = allSessions.size();
            long openSessions = allSessions.stream()
                    .filter(s -> "OPEN".equals(s.status()))
                    .count();
            long closedSessions = totalSessions - openSessions;

            model.addAttribute("totalSessions", totalSessions);
            model.addAttribute("openSessions", openSessions);
            model.addAttribute("closedSessions", closedSessions);
        }

        return "sessions";
    }

    @PostMapping("/web/sessions/open")
    public String openSession(@RequestParam Map<String, String> allParams,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            // Extract openingCash
            BigDecimal openingCash = new BigDecimal(allParams.getOrDefault("openingCash", "0"));

            // Extract float openings
            List<FloatOpeningRequest> floatReqs = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String balanceKey = "floatOpenings[" + i + "].openingBalance";
                String providerKey = "floatOpenings[" + i + "].providerId";

                if (allParams.containsKey(balanceKey) && allParams.containsKey(providerKey)) {
                    String balanceStr = allParams.get(balanceKey);
                    String providerStr = allParams.get(providerKey);

                    if (balanceStr != null && !balanceStr.isBlank()
                            && providerStr != null && !providerStr.isBlank()) {
                        floatReqs.add(new FloatOpeningRequest(
                                Long.parseLong(providerStr),
                                new BigDecimal(balanceStr)));
                    }
                }
            }

            String adjustmentNote = allParams.get("adjustmentNote");
            String notes = allParams.get("notes");

            OpenSessionRequest req = new OpenSessionRequest(
                    openingCash,
                    floatReqs,
                    adjustmentNote,
                    notes);
            sessionService.openSession(req, user);
            ra.addFlashAttribute("success", "Kikao kimefunguliwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/sessions";
    }

    @PostMapping("/web/sessions/{id}/close")
    public String closeSession(@PathVariable Long id,
            @RequestParam BigDecimal closingCash,
            @RequestParam(required = false) String notes,
            @RequestParam Map<String, String> allParams,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            List<FloatClosingRequest> floatClosings = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                String balanceKey = "floatClosings[" + i + "].closingBalance";
                String providerKey = "floatClosings[" + i + "].providerId";
                if (allParams.containsKey(balanceKey) && allParams.containsKey(providerKey)) {
                    String balanceStr = allParams.get(balanceKey);
                    String providerStr = allParams.get(providerKey);
                    if (balanceStr != null && !balanceStr.isBlank()
                            && providerStr != null && !providerStr.isBlank()) {
                        floatClosings.add(new FloatClosingRequest(
                                Long.parseLong(providerStr),
                                new BigDecimal(balanceStr)));
                    }
                }
            }

            CloseSessionRequest req = new CloseSessionRequest(closingCash, floatClosings, notes);
            sessionService.closeSession(id, req, user);
            ra.addFlashAttribute("success", "Kikao kimefungwa kikamilifu!");
            return "redirect:/web/reconciliation/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
            return "redirect:/web/sessions";
        }
    }

    @PostMapping("/web/sessions/{id}/reopen")
    public String reopenSession(@PathVariable Long id,
            @RequestParam String reason,
            @CurrentUser User user,
            RedirectAttributes ra) {
        if (user.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/web/sessions";
        }
        try {
            sessionService.reopenSession(id, reason, user);
            ra.addFlashAttribute("success",
                    "Kikao kimefunguliwa tena. Unaweza kuongeza matumizi au kubadilisha data.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/sessions/" + id;
    }

    @GetMapping("/web/sessions/{id}")
    public String sessionDetail(@PathVariable Long id,
            @CurrentUser User user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("sessionDetail", sessionService.getById(id));
        model.addAttribute("transactions", transactionService.getBySession(id));
        model.addAttribute("expenses", expenseService.getBySession(id));
        model.addAttribute("recon", reconciliationService.calculate(id));
        return "session-detail";
    }

    // ═══════════════════════════════════════
    // RECONCILIATION
    // ═══════════════════════════════════════
    @GetMapping("/web/reconciliation/{sessionId}")
    public String reconciliation(@PathVariable Long sessionId,
            @CurrentUser User user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("recon", reconciliationService.calculate(sessionId));

        // Ongeza analysis kwa OWNER
        if (user.getRole() == User.Role.OWNER) {
            try {
                model.addAttribute("analysis", shortageAnalysisService.analyze(sessionId));
            } catch (Exception e) {
                // Kama bado hakuna data ya kutosha
            }
        }
        return "reconciliation";
    }

    // ═══════════════════════════════════════
    // USERS (OWNER pekee)
    // ═══════════════════════════════════════
    @GetMapping("/web/users")
    public String users(@CurrentUser User user, Model model) {
        if (user.getRole() != User.Role.OWNER) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        model.addAttribute("users", userService.getAllUsers());
        return "users";
    }

    @PostMapping("/web/users")
    public String createUser(@RequestParam String username,
            @RequestParam String password,
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam com.Wakala.v1.Entity.User.Role role,
            @CurrentUser User currentUser,
            RedirectAttributes ra) {
        if (currentUser.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/dashboard";
        }

        try {
            UserRequest req = new UserRequest(username, password, fullName, phone, role);
            userService.createUser(req);
            ra.addFlashAttribute("success", "Mtumiaji ameongezwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/users";
    }

    // ═══════════════════════════════════════
    // USERS — DELETE
    // ═══════════════════════════════════════
    @PostMapping("/web/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
            @CurrentUser User currentUser,
            RedirectAttributes ra) {
        if (currentUser.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/dashboard";
        }

        try {
            userService.deleteUser(id, currentUser.getId());
            ra.addFlashAttribute("success", "Mtumiaji amefutwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/users";
    }

    // ═══════════════════════════════════════
    // PROFILE (watumiaji wote)
    // ═══════════════════════════════════════
    @GetMapping("/web/profile")
    public String profile(@CurrentUser User user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("profileForm", new UpdateUserRequest(user.getFullName(), user.getPhone()));
        return "profile";
    }

    @PostMapping("/web/profile")
    public String updateProfile(@ModelAttribute UpdateUserRequest req,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            userService.updateProfile(user.getId(), req);
            ra.addFlashAttribute("success", "Wasifu umesasishwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/profile";
    }

    @PostMapping("/web/profile/password")
    public String changePassword(@RequestParam String currentPassword,
            @RequestParam String newPassword,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            ChangePasswordRequest req = new ChangePasswordRequest(currentPassword, newPassword);
            userService.changePassword(user.getId(), req);
            ra.addFlashAttribute("success", "Password imebadilishwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/profile";
    }

    // ═══════════════════════════════════════
    // COMMISSION RULES
    // ═══════════════════════════════════════

    @GetMapping("/web/owner-rules")
    public String ownerRules(@CurrentUser User user, Model model) {
        if (user.getRole() != User.Role.OWNER) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        model.addAttribute("rules", ownerRuleService.getAllActive());
        model.addAttribute("networkRates", networkRateService.getAllActive());
        model.addAttribute("providers", providerService.getAllActive());
        model.addAttribute("transactionTypes", new Transaction.TransactionType[] {
                Transaction.TransactionType.LIPA_CASH_OUT
        });
        return "owner-rules";
    }

    @PostMapping("/web/owner-rules")
    public String createOwnerRule(@RequestParam Long providerId,
            @RequestParam Transaction.TransactionType transactionType,
            @RequestParam BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam BigDecimal ownerCommission,
            @RequestParam(required = false) String effectiveFrom,
            @RequestParam(required = false) String notes,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            LocalDate from = (effectiveFrom != null && !effectiveFrom.isBlank())
                    ? LocalDate.parse(effectiveFrom)
                    : LocalDate.now();

            OwnerRuleRequest req = new OwnerRuleRequest(
                    providerId, transactionType,
                    minAmount, maxAmount,
                    ownerCommission,
                    from, notes);
            ownerRuleService.create(req, user.getId());
            ra.addFlashAttribute("success", "Owner rule imeundwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/owner-rules";
    }

    @PostMapping("/web/owner-rules/{id}/supersede")
    public String supersedeOwnerRule(@PathVariable Long id,
            @RequestParam BigDecimal ownerCommission,
            @RequestParam String effectiveFrom,
            @RequestParam(required = false) String notes,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            UpdateOwnerRuleRequest req = new UpdateOwnerRuleRequest(
                    id, ownerCommission,
                    LocalDate.parse(effectiveFrom), notes);
            ownerRuleService.supersede(req, user.getId());
            ra.addFlashAttribute("success", "Rates zimebadilishwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/owner-rules";
    }

    @PostMapping("/web/owner-rules/{id}/deactivate")
    public String deactivateOwnerRule(@PathVariable Long id,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            ownerRuleService.deactivate(id, user.getId());
            ra.addFlashAttribute("success", "Rule imefungwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/owner-rules";
    }

    @PostMapping("/web/commission-rules")
    public String createRule(@RequestParam Long providerId,
            @RequestParam Transaction.TransactionType transactionType,
            @RequestParam BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam BigDecimal networkCommission,
            @RequestParam BigDecimal ownerCommission,
            @RequestParam(required = false) String effectiveFrom,
            @RequestParam(required = false) String notes,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            LocalDate from = (effectiveFrom != null && !effectiveFrom.isBlank())
                    ? LocalDate.parse(effectiveFrom)
                    : LocalDate.now();

            CommissionRuleRequest req = new CommissionRuleRequest(
                    providerId, transactionType,
                    minAmount, maxAmount,
                    networkCommission, ownerCommission,
                    from, notes);
            commissionRuleService.create(req, user.getId());
            ra.addFlashAttribute("success", "Commission rule imeundwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/commission-rules";
    }

    @PostMapping("/web/commission-rules/{id}/supersede")
    public String supersedeRule(@PathVariable Long id,
            @RequestParam BigDecimal networkCommission,
            @RequestParam BigDecimal ownerCommission,
            @RequestParam String effectiveFrom,
            @RequestParam(required = false) String notes,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            UpdateCommissionRuleRequest req = new UpdateCommissionRuleRequest(
                    id, networkCommission, ownerCommission,
                    LocalDate.parse(effectiveFrom), notes);
            commissionRuleService.supersede(req, user.getId());
            ra.addFlashAttribute("success", "Rates zimebadilishwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/commission-rules";
    }

    @PostMapping("/web/commission-rules/{id}/deactivate")
    public String deactivateRule(@PathVariable Long id,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            commissionRuleService.deactivate(id, user.getId());
            ra.addFlashAttribute("success", "Rule imefungwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/commission-rules";
    }

    @PostMapping("/web/transactions/{id}/void")
    public String voidTransaction(@PathVariable Long id,
            @RequestParam String reason,
            @CurrentUser User user,
            RedirectAttributes ra) {
        if (user.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Ni OWNER pekee anayeweza kuviodi transaction");
            return "redirect:/web/transactions";
        }

        try {
            VoidTransactionRequest req = new VoidTransactionRequest(reason);
            transactionService.voidTransaction(id, req, user.getId());
            ra.addFlashAttribute("success", "Transaction imeviodiwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/transactions";
    }

    // ═══════════════════════════════════════
    // PROVIDERS
    // ═══════════════════════════════════════
    @GetMapping("/web/providers")
    public String providers(@CurrentUser User user, Model model) {
        if (user.getRole() != User.Role.OWNER) {
            return "redirect:/dashboard";
        }
        model.addAttribute("user", user);
        model.addAttribute("providers", providerService.getAllActive());
        return "providers";
    }

    @PostMapping("/web/providers")
    public String createProvider(@RequestParam String name,
            @RequestParam Provider.ProviderType type,
            @CurrentUser User user,
            RedirectAttributes ra) {
        if (user.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/dashboard";
        }
        try {
            ProviderRequest req = new ProviderRequest(name, type);
            providerService.createProvider(req);
            ra.addFlashAttribute("success", "Provider ameongezwa kikamilifu!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/providers";
    }

    // ═══════════════════════════════════════
    // EXPENSES
    // ═══════════════════════════════════════
    @GetMapping("/web/expenses")
    public String expenses(@CurrentUser User user, Model model) {
        model.addAttribute("user", user);

        SessionResponse todaySession = sessionService.getTodaySession();
        model.addAttribute("todaySession", todaySession);

        if (todaySession != null) {
            List<ExpenseResponse> expenses = expenseService.getBySession(todaySession.id());
            model.addAttribute("expenses", expenses);

            // Jumla ya matumizi
            BigDecimal total = expenses.stream()
                    .filter(e -> "APPROVED".equals(e.status()))
                    .map(ExpenseResponse::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("totalExpenses", total);
        }

        // Pending expenses kwa OWNER
        if (user.getRole() == User.Role.OWNER) {
            model.addAttribute("pendingExpenses", expenseService.getPending());
        } else {
            model.addAttribute("pendingExpenses", List.of());
        }

        return "expenses";
    }

    @PostMapping("/web/expenses")
    public String recordExpense(@ModelAttribute ExpenseForm form,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            SessionResponse todaySession = sessionService.getTodaySession();
            if (todaySession == null) {
                ra.addFlashAttribute("error", "Hakuna kikao wazi. Fungua kikao kwanza.");
                return "redirect:/web/expenses";
            }

            ExpenseRequest req = new ExpenseRequest(
                    todaySession.id(),
                    form.getDescription(),
                    form.getAmount(),
                    form.getCategory(),
                    form.getReceiptNumber());
            expenseService.record(req, user);
            ra.addFlashAttribute("success", "Matumizi yamerekodiwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/expenses";
    }

    @PostMapping("/web/expenses/{id}/approve")
    public String approveExpense(@PathVariable Long id,
            @CurrentUser User user,
            RedirectAttributes ra) {
        if (user.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/web/expenses";
        }
        try {
            ApproveExpenseRequest req = new ApproveExpenseRequest(id, true);
            expenseService.approve(req, user.getId());
            ra.addFlashAttribute("success", "Matumizi yameidhinishwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/expenses";
    }

    @PostMapping("/web/expenses/{id}/reject")
    public String rejectExpense(@PathVariable Long id,
            @CurrentUser User user,
            RedirectAttributes ra) {
        if (user.getRole() != User.Role.OWNER) {
            ra.addFlashAttribute("error", "Huna ruhusa");
            return "redirect:/web/expenses";
        }
        try {
            ApproveExpenseRequest req = new ApproveExpenseRequest(id, false);
            expenseService.approve(req, user.getId());
            ra.addFlashAttribute("success", "Matumizi yamekataliwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/expenses";
    }

    @PostMapping("/web/expenses/{id}/cancel")
    public String cancelExpense(@PathVariable Long id,
            @CurrentUser User user,
            RedirectAttributes ra) {
        try {
            CancelExpenseRequest req = new CancelExpenseRequest("Ilifutwa na mtumiaji");
            expenseService.cancel(id, req, user.getId());
            ra.addFlashAttribute("success", "Matumizi yamefutwa!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Kosa: " + e.getMessage());
        }
        return "redirect:/web/expenses";
    }

    @GetMapping("/web/owner-rules/preview")
    @ResponseBody
    public ResponseEntity<?> previewNetworkRate(
            @RequestParam Long providerId,
            @RequestParam Transaction.TransactionType type,
            @RequestParam BigDecimal amount) {
        try {
            NetworkRate rate = networkRateService.findApplicableRate(providerId, type, amount);
            Map<String, Object> result = new HashMap<>();
            result.put("exists", true);
            result.put("networkCommission", rate.getNetworkCommission());
            result.put("minAmount", rate.getMinAmount());
            result.put("maxAmount", rate.getMaxAmount());
            result.put("effectiveFrom", rate.getEffectiveFrom().toString());
            result.put("effectiveTo", rate.getEffectiveTo() != null
                    ? rate.getEffectiveTo().toString()
                    : null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("exists", false);
            result.put("message", e.getMessage());
            return ResponseEntity.ok(result);
        }
    }
}