package com.carddemo.web.user;

import com.carddemo.domain.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Delete user — replaces COUSR03C (467 LOC).
 * SEC-009: requires ROLE_ADMIN.
 */
@Controller
@RequestMapping("/admin/users/delete")
@PreAuthorize("hasRole('ADMIN')")
public class UserDeleteController {

    private final UserRepository userRepository;

    public UserDeleteController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public String deleteUserForm(@RequestParam(required = false) String userId, Model model) {
        if (userId != null) {
            userRepository.findById(userId.toUpperCase())
                .ifPresent(u -> model.addAttribute("user", u));
        }
        return "user/delete";
    }

    @PostMapping
    public String deleteUser(@RequestParam String userId, Model model) {
        String id = userId.toUpperCase();
        if (!userRepository.existsById(id)) {
            model.addAttribute("error", "User not found: " + userId);
            return "user/delete";
        }
        userRepository.deleteById(id);
        return "redirect:/admin/users?deleted=true";
    }
}
