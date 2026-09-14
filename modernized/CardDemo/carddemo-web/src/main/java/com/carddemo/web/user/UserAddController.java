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
 * Add user — replaces COUSR01C (450 LOC).
 * SEC-009: requires ROLE_ADMIN. Password hashed with BCrypt before storage (SEC-003).
 */
@Controller
@RequestMapping("/admin/users/add")
@PreAuthorize("hasRole('ADMIN')")
public class UserAddController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAddController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String addUserForm() {
        return "user/add";
    }

    @PostMapping
    public String addUser(@RequestParam String userId,
                          @RequestParam String firstName,
                          @RequestParam String lastName,
                          @RequestParam String password,
                          @RequestParam String userType,
                          Model model) {
        // SEC-019: BCrypt silently truncates passwords > 72 bytes (CVE-2025-22228).
        if (password == null || password.isBlank()) {
            model.addAttribute("error", "Password is required.");
            return "user/add";
        }
        if (password.length() > 72) {
            model.addAttribute("error", "Password must be 72 characters or fewer (BCrypt limit — CVE-2025-22228).");
            return "user/add";
        }
        if (userRepository.existsById(userId.toUpperCase())) {
            model.addAttribute("error", "User ID already exists.");
            return "user/add";
        }
        userRepository.save(new UserEntity(
            userId.toUpperCase(), firstName, lastName,
            passwordEncoder.encode(password), userType));
        return "redirect:/admin/users?added=true";
    }
}
