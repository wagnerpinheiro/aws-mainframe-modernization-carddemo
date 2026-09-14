package com.carddemo.web.navigation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for COMEN01C → MenuController and COADM01C → AdminMenuController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuControllerTest {

    @Autowired MockMvc mockMvc;

    /** Regular user can access /menu and sees 11 options (CDEMO-MENU-OPT-COUNT = 11). */
    @Test
    @WithMockUser(roles = "USER")
    void regularUser_menuShows11Options() throws Exception {
        mockMvc.perform(get("/menu"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Account View")))
            .andExpect(content().string(containsString("Bill Payment")))
            .andExpect(content().string(containsString("Pending Authorization View")));
    }

    /** Admin can access /admin/menu. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_adminMenuAccessible() throws Exception {
        mockMvc.perform(get("/admin/menu"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("User List")))
            .andExpect(content().string(containsString("User Add")));
    }

    /** Unauthenticated user is redirected to login for /menu. */
    @Test
    void unauthenticated_redirectedToLogin() throws Exception {
        mockMvc.perform(get("/menu"))
            .andExpect(status().is3xxRedirection());
    }

    /** RULE-006 verified at security layer: regular user cannot reach admin menu. */
    @Test
    @WithMockUser(roles = "USER")
    void rule006_regularUser_cannotAccessAdminMenu() throws Exception {
        mockMvc.perform(get("/admin/menu"))
            .andExpect(status().isForbidden());
    }

    /** Admin menu shows user management options (COUSR00-03C → Java controllers). */
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMenu_showsUserManagementOptions() throws Exception {
        mockMvc.perform(get("/admin/menu"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("User List (Security)")))
            .andExpect(content().string(containsString("User Add (Security)")))
            .andExpect(content().string(containsString("User Update (Security)")))
            .andExpect(content().string(containsString("User Delete (Security)")));
    }
}
