package com.carddemo.web.user;

import com.carddemo.domain.entity.UserEntity;
import com.carddemo.domain.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Update user — replaces COUSR02C (450 LOC).
 * SEC-009: requires ROLE_ADMIN. Password hashed with BCrypt before storage (SEC-003).
 */
@Controller
@RequestMapping("/admin/users/update")
@PreAuthorize("hasRole('ADMIN')")
public class UserUpdateController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserUpdateController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String updateUserForm(@RequestParam(required = false) String userId, Model model) {
        if (userId != null) {
            userRepository.findById(userId.toUpperCase())
                .ifPresent(u -> model.addAttribute("user", u));
        }
        return "user/update";
    }

    @PostMapping
    public String updateUser(@RequestParam String userId,
                             @RequestParam String firstName,
                             @RequestParam String lastName,
                             @RequestParam(required = false) String password,
                             @RequestParam String userType,
                             Model model) {
        return userRepository.findById(userId.toUpperCase()).map(existing -> {
            String hashed = (password != null && !password.isBlank())
                ? passwordEncoder.encode(password)
                : existing.getPassword();
            userRepository.save(new UserEntity(
                existing.getUserId(), firstName, lastName, hashed, userType));
            return "redirect:/admin/users?updated=true";
        }).orElseGet(() -> {
            model.addAttribute("error", "User not found: " + userId);
            return "user/update";
        });
    }
}
