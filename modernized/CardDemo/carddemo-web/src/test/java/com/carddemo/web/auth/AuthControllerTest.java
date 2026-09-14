package com.carddemo.web.auth;

import com.carddemo.domain.entity.UserEntity;
import com.carddemo.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for COSGN00C → AuthController.
 *
 * Covers RULE-001 through RULE-006 (COSGN00C PROCESS-ENTER-KEY logic).
 *
 * SEC-003 fix verified: Spring Security uses BCrypt; plaintext comparison is gone.
 * SEC-009: admin-only endpoint access control tested in RULE-006.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String ADMIN_ID = "ADMINUSR";
    private static final String USER_ID  = "NORMALUS";
    private static final String PASSWORD  = "Secret01";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new UserEntity(ADMIN_ID, "Admin", "User",
            passwordEncoder.encode(PASSWORD), "A"));
        userRepository.save(new UserEntity(USER_ID, "Normal", "User",
            passwordEncoder.encode(PASSWORD), "U"));
    }

    /** RULE-001: blank userId → authentication failure → redirect to /login?error */
    @Test
    void rule001_blankUserId_authFailure() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", "")
                .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"))
            .andExpect(unauthenticated());
    }

    /** RULE-002: blank password → authentication failure */
    @Test
    void rule002_blankPassword_authFailure() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", USER_ID)
                .param("password", ""))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"))
            .andExpect(unauthenticated());
    }

    /** RULE-003: unknown userId → authentication failure */
    @Test
    void rule003_unknownUser_authFailure() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", "NOSUCHER")
                .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"))
            .andExpect(unauthenticated());
    }

    /** RULE-004: wrong password → authentication failure (BCrypt, not plaintext — SEC-003) */
    @Test
    void rule004_wrongPassword_authFailure() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", USER_ID)
                .param("password", "WrongPwd"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"))
            .andExpect(unauthenticated());
    }

    /** RULE-005: admin credentials → redirect to /admin/menu (was XCTL COADM01C) */
    @Test
    void rule005_adminLogin_redirectsToAdminMenu() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", ADMIN_ID)
                .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/menu"))
            .andExpect(authenticated().withRoles("ADMIN"));
    }

    /** RULE-005 (user branch): regular user → redirect to /menu (was XCTL COMEN01C) */
    @Test
    void rule005_regularUserLogin_redirectsToMenu() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", USER_ID)
                .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/menu"))
            .andExpect(authenticated().withRoles("USER"));
    }

    /** RULE-006: regular user cannot access /admin/menu → 302 to login */
    @Test
    void rule006_regularUserBlockedFromAdminMenu() throws Exception {
        mockMvc.perform(get("/admin/menu")
                .with(user(USER_ID).roles("USER")))
            .andExpect(status().isForbidden());
    }

    /** RULE-055: logout invalidates session (COSGN00C PF3 → EXEC CICS RETURN) */
    @Test
    void rule055_logout_invalidatesSession() throws Exception {
        mockMvc.perform(post("/logout").with(csrf())
                .with(user(USER_ID).roles("USER")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?logout=true"))
            .andExpect(unauthenticated());
    }

    /** Login page is publicly accessible. */
    @Test
    void loginPage_isPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }
}
