package com.carddemo.web.navigation;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

/**
 * Admin menu — replaces COADM01C (296 LOC).
 *
 * COADM02Y defines 6 admin options (CDEMO-ADMIN-OPT-COUNT = 6):
 * 1–4 are user management (COUSR00-03C). Options 5–6 (Db2 transaction type
 * maintenance) are PoC-deferred stubs.
 *
 * SEC-009 fix: @PreAuthorize("hasRole('ADMIN')") — COADM01C had no authorization
 * check; any authenticated user could reach the admin menu via COMMAREA.
 */
@Controller
public class AdminMenuController {

    record MenuItem(int number, String name, String url, boolean available) {}

    private static final List<MenuItem> ADMIN_OPTIONS = List.of(
        new MenuItem(1, "User List (Security)",               "/admin/users",         true),
        new MenuItem(2, "User Add (Security)",                "/admin/users/add",     true),
        new MenuItem(3, "User Update (Security)",             "/admin/users/update",  true),
        new MenuItem(4, "User Delete (Security)",             "/admin/users/delete",  true),
        new MenuItem(5, "Transaction Type List/Update (Db2)", "#",                    false),
        new MenuItem(6, "Transaction Type Maintenance (Db2)", "#",                    false)
    );

    @GetMapping("/admin/menu")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminMenu(Model model) {
        model.addAttribute("options", ADMIN_OPTIONS);
        return "admin-menu";
    }
}
