package com.Wakala.v1.Service;

import com.Wakala.v1.Dto.AuthResponse;
import com.Wakala.v1.Dto.CurrentUserResponse;
import com.Wakala.v1.Dto.LoginRequest;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.UserRepository;
import com.Wakala.v1.Security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest req) {
        try {
            // 1. Authenticate
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.username(), req.password()));

            // 2. Thibitisha authentication ilifanikiwa
            if (!auth.isAuthenticated()) {
                throw new BadCredentialsException("Authentication imeshindwa");
            }

            // 3. Chukua username kutoka auth (sio request)
            String authenticatedUsername = auth.getName();

            // 4. Chukua User entity kutoka DB
            User user = userRepository.findByUsername(authenticatedUsername)
                    .orElseThrow(() -> new ResourceNotFoundException("User hapatikani"));

            // 5. Generate token
            String token = jwtService.generateToken(
                    org.springframework.security.core.userdetails.User.builder()
                            .username(user.getUsername())
                            .password(user.getPassword())
                            .authorities("ROLE_" + user.getRole().name())
                            .build(),
                    user.getRole().name(),
                    user.getId());

            // 6. Audit log
            auditService.log(user.getId(), "LOGIN", "User", user.getId(), null, null);

            return new AuthResponse(
                    token,
                    user.getUsername(),
                    user.getFullName(),
                    user.getRole().name(),
                    user.getId());

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Username au password si sahihi");
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().name());
    }

}