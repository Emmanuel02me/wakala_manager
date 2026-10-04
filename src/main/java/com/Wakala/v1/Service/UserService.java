package com.Wakala.v1.Service;

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

    @Transactional
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username tayari upo: " + request.username());
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

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mtumiaji huyu hapatikani: " + id));
    }

    public UserResponse getUser(Long id) {
        return toResponse(findById(id));
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(),
                u.getPhone(), u.getRole().name());
    }

    @Transactional
    public void deleteUser(Long userId, Long currentUserId) {
        if (userId.equals(currentUserId)) {
            throw new BusinessException("Hauwezi kujifuta mwenyewe");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User haipatikani"));

        // Kama ni OWNER wa mwisho, hawezi kufutwa
        if (user.getRole() == User.Role.OWNER) {
            long ownerCount = userRepository.countByRole(User.Role.OWNER);
            if (ownerCount <= 1) {
                throw new BusinessException("Hauwezi kufuta OWNER wa mwisho");
            }
        }

        userRepository.delete(user);
    }

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
}