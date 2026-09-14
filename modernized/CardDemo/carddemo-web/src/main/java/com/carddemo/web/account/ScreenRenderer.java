package com.carddemo.web.account;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CustomerEntity;
import org.springframework.ui.Model;

/**
 * Populates Spring MVC Model attributes for account display screens.
 *
 * Replaces COACTUPC BMS map population logic (MOVE fields to BMS map fields).
 * Extracted as decomposition step 1, before AccountUpdateController is written.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
public final class ScreenRenderer {

    private ScreenRenderer() {}

    /** Populates model for the read-only account view (COACTVWC). */
    public static void populateAccountView(Model model, AccountEntity account, CustomerEntity customer) {
        model.addAttribute("account", account);
        model.addAttribute("customer", customer);
        model.addAttribute("fullName", buildFullName(customer));
    }

    /** Populates model for the account edit form (COACTUPC). */
    public static void populateAccountEditForm(Model model, AccountEntity account, CustomerEntity customer) {
        populateAccountView(model, account, customer);
        AccountUpdateForm form = new AccountUpdateForm(
            account.getId(), account.getVersion(),
            account.getActiveStatus(), account.getCreditLimit(), account.getCashCreditLimit(),
            customer != null ? customer.getFirstName() : "",
            customer != null ? customer.getMiddleName() : "",
            customer != null ? customer.getLastName() : "",
            customer != null ? customer.getAddrLine1() : "",
            customer != null ? customer.getAddrLine2() : "",
            customer != null ? customer.getAddrLine3() : "",
            customer != null ? customer.getAddrStateCd() : "",
            customer != null ? customer.getAddrZip() : "",
            customer != null ? customer.getAddrLine3() : "",
            customer != null ? customer.getAddrCountryCd() : "",
            customer != null ? customer.getPhoneNum1() : "",
            customer != null ? customer.getPhoneNum2() : "",
            customer != null ? customer.getFicoCreditScore() : null,
            customer != null ? customer.getEftAccountId() : "",
            customer != null ? customer.getPriCardHolderInd() : ""
        );
        model.addAttribute("form", form);
    }

    private static String buildFullName(CustomerEntity c) {
        if (c == null) return "";
        StringBuilder sb = new StringBuilder();
        if (c.getFirstName() != null) sb.append(c.getFirstName().strip());
        if (c.getMiddleName() != null && !c.getMiddleName().isBlank()) sb.append(' ').append(c.getMiddleName().strip());
        if (c.getLastName() != null) sb.append(' ').append(c.getLastName().strip());
        return sb.toString().strip();
    }
}
