package com.carddemo.web.user;

import com.carddemo.domain.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * User list — replaces COUSR00C (400 LOC).
 *
 * SEC-009 fix: @PreAuthorize("hasRole('ADMIN')") added here.
 * The COBOL had no authorization check — any authenticated session could reach
 * COUSR00C via COMMAREA if they knew the TRANSID.
 */
@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserListController {

    private final UserRepository userRepository;

    public UserListController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "user/list";
    }
}
