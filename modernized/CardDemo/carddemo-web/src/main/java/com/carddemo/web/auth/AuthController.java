package com.carddemo.web.auth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves the login page.
 *
 * Replaces COSGN00C SEND-SIGNON-SCREEN (EXEC CICS SEND MAP COSGN0A).
 * POST /login is handled entirely by Spring Security's form login filter —
 * no explicit @PostMapping needed here.
 *
 * RULE-001–004 outcomes (blank user/pass, unknown user, wrong password) are
 * signaled by Spring Security redirecting to /login?error=true.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid user ID or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been signed out.");
        }
        return "login";
    }
}
