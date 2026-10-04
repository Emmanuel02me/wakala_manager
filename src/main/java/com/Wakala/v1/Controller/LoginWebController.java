package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.AuthResponse;
import com.Wakala.v1.Dto.LoginRequest;
import com.Wakala.v1.Service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class LoginWebController {

    private final AuthService authService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String redirect, Model model) {
        model.addAttribute("redirect", redirect);
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute LoginRequest req,
            @RequestParam(required = false) String redirect,
            HttpServletResponse response, Model model) {
        try {
            AuthResponse auth = authService.login(req);

            // Cookie 1: JWT (httpOnly)
            Cookie jwtCookie = new Cookie("jwt", auth.token());
            jwtCookie.setHttpOnly(true);
            jwtCookie.setPath("/");
            jwtCookie.setMaxAge(-1); // Session cookie
            response.addCookie(jwtCookie);

            // Cookie 2: logged_in (not httpOnly, kwa JS)
            Cookie loggedInCookie = new Cookie("logged_in", "1");
            loggedInCookie.setHttpOnly(false);
            loggedInCookie.setPath("/");
            loggedInCookie.setMaxAge(-1); // Session cookie
            response.addCookie(loggedInCookie);

            // ✅ Redirect kwa original URL au dashboard
            if (redirect != null && !redirect.isBlank()) {
                return "redirect:" + redirect;
            }
            return "redirect:/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Username au password si sahihi");
            model.addAttribute("redirect", redirect);
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        SecurityContextHolder.clearContext();

        Cookie jwtCookie = new Cookie("jwt", null);
        jwtCookie.setHttpOnly(true);
        jwtCookie.setPath("/");
        jwtCookie.setMaxAge(0);
        response.addCookie(jwtCookie);

        Cookie loggedInCookie = new Cookie("logged_in", null);
        loggedInCookie.setHttpOnly(false);
        loggedInCookie.setPath("/");
        loggedInCookie.setMaxAge(0);
        response.addCookie(loggedInCookie);

        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        return "redirect:/login?logout";
    }
}