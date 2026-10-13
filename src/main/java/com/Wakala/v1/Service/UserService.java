package com.Wakala.v1.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.Wakala.v1.Dto.ChangePasswordRequest;
import com.Wakala.v1.Dto.UpdateUserRequest;
import com.Wakala.v1.Dto.UserRequest;
import com.Wakala.v1.Dto.UserResponse;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Exception.BusinessException;
import com.Wakala.v1.Exception.ResourceNotFoundException;
import com.Wakala.v1.Repositories.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ═══════════════════════════════════════════════════════════
    // CREATE
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public UserResponse createUser(UserRequest request) {
        // Zuia SUPER_ADMIN kuundwa kupitia UI
        if (request.role() == User.Role.SUPER_ADMIN) {
            throw new BusinessException("Hauwezi kuunda System Admin");
        }

        // Username unique — kama ipo (active au deleted) zuia
        if (userRepository.existsByUsername(request.username())) {
            // Angalia kama ni user aliyeondolewa
            User existing = userRepository.findByUsername(request.username()).orElse(null);
            if (existing != null && !existing.isActive()) {
                throw new BusinessException(
                        "Username '" + request.username() + "' ilitumika na mtumiaji aliyeondolewa. " +
                                "Rudisha mtumiaji huyo kwanza au chagua jina lingine.");
            }
            throw new BusinessException("Username tayari inatumika: " + request.username());
        }

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(request.role())
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    // ═══════════════════════════════════════════════════════════
    // READ
    // ═══════════════════════════════════════════════════════════
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Mtumiaji huyu hapatikani: " + id));
    }

    public UserResponse getUser(Long id) {
        return toResponse(findById(id));
    }

    /** Orodha ya watumiaji HAI pekee — inatumika kwenye UI ya kawaida */
    public List<UserResponse> getAllUsers() {
        return userRepository.findByActiveTrueOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    /** Orodha ya WOTE — pamoja na waliofutwa. Kwa OWNER pekee. */
    public List<UserResponse> getAllUsersIncludingDeleted() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).toList();
    }

    // ═══════════════════════════════════════════════════════════
    // SOFT DELETE — haifuti kabisa, inaweka active=false
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public void deleteUser(Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException("Hauwezi kujifuta mwenyewe");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        if (!user.isActive()) {
            throw new BusinessException("Mtumiaji huyu ameondolewa tayari");
        }

        // Kinga: OWNER wa mwisho ALIYE HAI hawezi kuondolewa
        if (user.getRole() == User.Role.OWNER) {
            long activeOwners = userRepository.countByRoleAndActiveTrue(User.Role.OWNER);
            if (activeOwners <= 1) {
                throw new BusinessException("Hauwezi kuondoa OWNER wa mwisho aliye hai");
            }
        }

        // ✅ SOFT DELETE — audit_logs FK inabaki intact
        user.setActive(false);
        user.setDeletedAt(LocalDateTime.now());
        user.setDeletedBy(currentUserId);
        userRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════════
    // RESTORE — kurudisha user aliyeondolewa
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public void restoreUser(Long userId, Long currentUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        if (user.isActive()) {
            throw new BusinessException("Mtumiaji huyu hayupo kwenye hali ya kuondolewa");
        }

        user.setActive(true);
        user.setDeletedAt(null);
        user.setDeletedBy(null);
        userRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE
    // ═══════════════════════════════════════════════════════════
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        user.setFullName(req.fullName());
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BusinessException("Password ya sasa si sahihi");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
    }

    // ═══════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════
    private UserResponse toResponse(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getPhone(),
                u.getRole().name(),
                u.isActive(),
                u.getDeletedAt());
    }
}