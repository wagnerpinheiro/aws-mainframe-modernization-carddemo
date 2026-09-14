package com.carddemo.web.user;

import com.carddemo.domain.entity.UserEntity;
import com.carddemo.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests SEC-009 fix: @PreAuthorize("hasRole('ADMIN')") on all user management endpoints.
 *
 * COUSR00-03C had no authorization check in the COBOL — any user who knew the TRANSID
 * could reach user management functions. This test verifies the fix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementSecurityTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(new UserEntity("ADMINUSR", "Admin", "Test",
            passwordEncoder.encode("pass"), "A"));
    }

    /** SEC-009: regular user cannot GET /admin/users */
    @Test
    @WithMockUser(roles = "USER")
    void sec009_regularUser_cannotListUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isForbidden());
    }

    /** SEC-009: admin can GET /admin/users */
    @Test
    @WithMockUser(roles = "ADMIN")
    void sec009_admin_canListUsers() throws Exception {
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().isOk());
    }

    /** SEC-009: regular user cannot GET /admin/users/add */
    @Test
    @WithMockUser(roles = "USER")
    void sec009_regularUser_cannotAccessAddUser() throws Exception {
        mockMvc.perform(get("/admin/users/add"))
            .andExpect(status().isForbidden());
    }

    /** SEC-009: regular user cannot POST /admin/users/add */
    @Test
    @WithMockUser(roles = "USER")
    void sec009_regularUser_cannotPostAddUser() throws Exception {
        mockMvc.perform(post("/admin/users/add").with(csrf())
                .param("userId", "NEWUSER1")
                .param("firstName", "New").param("lastName", "User")
                .param("password", "pass").param("userType", "U"))
            .andExpect(status().isForbidden());
    }

    /** SEC-009: admin can access user update */
    @Test
    @WithMockUser(roles = "ADMIN")
    void sec009_admin_canAccessUpdateUser() throws Exception {
        mockMvc.perform(get("/admin/users/update").param("userId", "ADMINUSR"))
            .andExpect(status().isOk());
    }

    /** SEC-009: regular user cannot access user delete */
    @Test
    @WithMockUser(roles = "USER")
    void sec009_regularUser_cannotDeleteUser() throws Exception {
        mockMvc.perform(get("/admin/users/delete").param("userId", "ADMINUSR"))
            .andExpect(status().isForbidden());
    }
}
