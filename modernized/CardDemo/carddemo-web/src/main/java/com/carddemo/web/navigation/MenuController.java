package com.carddemo.web.navigation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Main menu for regular users — replaces COMEN01C (300 LOC).
 *
 * COMEN02Y defines 11 menu options (CDEMO-MENU-OPT-COUNT = 11), all type 'U'.
 * RULE-006: admin-only options are blocked at the SecurityConfig level (/admin/**).
 * Option 11 (COPAUS0C — Pending Authorization View, D9 scope) returns HTTP 503
 * per brief (not installed in PoC).
 *
 * RULE-055: session is managed by Spring Security (login/logout).
 */
@Controller
public class MenuController {

    record MenuItem(int number, String name, String url) {}

    private static final List<MenuItem> MENU_OPTIONS = List.of(
        new MenuItem(1,  "Account View",              "/accounts/view"),
        new MenuItem(2,  "Account Update",            "/accounts/update"),
        new MenuItem(3,  "Credit Card List",          "/cards/list"),
        new MenuItem(4,  "Credit Card View",          "/cards/view"),
        new MenuItem(5,  "Credit Card Update",        "/cards/update"),
        new MenuItem(6,  "Transaction List",          "/transactions/list"),
        new MenuItem(7,  "Transaction View",          "/transactions/view"),
        new MenuItem(8,  "Transaction Add",           "/transactions/add"),
        new MenuItem(9,  "Transaction Reports",       "/reports/trigger"),
        new MenuItem(10, "Bill Payment",              "/billing/pay"),
        new MenuItem(11, "Pending Authorization View","/auth-ext/pending")
    );

    @GetMapping("/menu")
    @PreAuthorize("isAuthenticated()")
    public String menu(Model model) {
        model.addAttribute("options", MENU_OPTIONS);
        return "menu";
    }
}
