package com.Wakala.v1.Controller;

import com.Wakala.v1.Dto.UserRequest;
import com.Wakala.v1.Dto.UserResponse;
import com.Wakala.v1.Entity.User;
import com.Wakala.v1.Security.CurrentUser;
import com.Wakala.v1.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Orodha ya watumiaji HAI pekee */
    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<UserResponse> getAll() {
        return userService.getAllUsers();
    }

    /** Orodha ya WOTE — pamoja na waliofutwa */
    @GetMapping("/all-including-deleted")
    @PreAuthorize("hasRole('OWNER')")
    public List<UserResponse> getAllIncludingDeleted() {
        return userService.getAllUsersIncludingDeleted();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'EMPLOYEE')")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> restore(
            @PathVariable Long id,
            @CurrentUser User currentUser) {
        userService.restoreUser(id, currentUser.getId());
        return ResponseEntity.ok().build();
    }
}