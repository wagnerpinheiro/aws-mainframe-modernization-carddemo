package com.carddemo.web.account;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.entity.UserEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.CustomerRepository;
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

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for COACTVWC + COACTUPC → AccountViewController + AccountUpdateController.
 *
 * COACTVWC oracle: read-only display of account + customer fields.
 * COACTUPC oracle: RULE-024–045 validation, RULE-053 optimistic lock, RULE-061 atomic rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CardXRefRepository cardXRefRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        cardXRefRepository.deleteAll();
        customerRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // =========================================================================
    // COACTVWC — read-only account view
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void viewAccount_displaysAccountAndCustomerFields() throws Exception {
        long acctId = 10001L, custId = 9001L;
        accountRepository.save(new AccountEntity(
            acctId, "Y", new BigDecimal("1500.00"), new BigDecimal("5000.00"),
            new BigDecimal("2500.00"), "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "98101", "TESTGRP"
        ));
        customerRepository.save(new CustomerEntity(
            custId, "Alice", "M", "Smith",
            "123 Main St", "", "", "WA", "USA", "98101",
            "", "", 123456789L, "GOV001", "1990-01-01", "EFT0000001", "Y", 720
        ));
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", custId, acctId));

        mockMvc.perform(get("/accounts/" + acctId))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("10001")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void viewAccount_notFound_returns200WithError() throws Exception {
        mockMvc.perform(get("/accounts/99999"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("not found")));
    }

    // =========================================================================
    // COACTUPC — validation (RULE-024–045)
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAccount_blankFirstName_failsRule035() throws Exception {
        long acctId = 20001L, custId = 9002L;
        seedAccountAndCustomer(acctId, custId);

        mockMvc.perform(post("/accounts/" + acctId + "/edit")
                .with(csrf())
                .param("accountId", String.valueOf(acctId))
                .param("version", "0")
                .param("activeStatus", "Y")
                .param("creditLimit", "5000.00")
                .param("cashCreditLimit", "2500.00")
                .param("firstName", "")        // RULE-035: blank = invalid
                .param("lastName", "Smith")
                .param("addrLine1", "123 Main")
                .param("addrStateCd", "WA")
                .param("addrZip", "98101")
                .param("addrCity", "Seattle")
                .param("addrCountryCd", "USA")
                .param("ficoCreditScore", "700")
                .param("eftAccountId", "1234567890")
                .param("priCardHolderInd", "Y"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("RULE-035")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAccount_invalidFico_failsRule034() throws Exception {
        long acctId = 20002L, custId = 9003L;
        seedAccountAndCustomer(acctId, custId);

        mockMvc.perform(post("/accounts/" + acctId + "/edit")
                .with(csrf())
                .param("accountId", String.valueOf(acctId))
                .param("version", "0")
                .param("activeStatus", "Y")
                .param("creditLimit", "5000.00")
                .param("cashCreditLimit", "2500.00")
                .param("firstName", "Alice")
                .param("lastName", "Smith")
                .param("addrLine1", "123 Main")
                .param("addrStateCd", "WA")
                .param("addrZip", "98101")
                .param("addrCity", "Seattle")
                .param("addrCountryCd", "USA")
                .param("ficoCreditScore", "100") // RULE-034: below 300
                .param("eftAccountId", "1234567890")
                .param("priCardHolderInd", "Y"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("RULE-034")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAccount_invalidState_failsRule037() throws Exception {
        long acctId = 20003L, custId = 9004L;
        seedAccountAndCustomer(acctId, custId);

        mockMvc.perform(post("/accounts/" + acctId + "/edit")
                .with(csrf())
                .param("accountId", String.valueOf(acctId))
                .param("version", "0")
                .param("activeStatus", "Y")
                .param("creditLimit", "5000.00")
                .param("cashCreditLimit", "2500.00")
                .param("firstName", "Alice")
                .param("lastName", "Smith")
                .param("addrLine1", "123 Main")
                .param("addrStateCd", "XX")  // RULE-037: invalid state
                .param("addrZip", "98101")
                .param("addrCity", "Seattle")
                .param("addrCountryCd", "USA")
                .param("ficoCreditScore", "700")
                .param("eftAccountId", "1234567890")
                .param("priCardHolderInd", "Y"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("RULE-037")));
    }

    // =========================================================================
    // RULE-053: optimistic locking — @Version field present, controller returns 409 on stale
    // =========================================================================

    /**
     * RULE-053: AccountEntity has @Version field; controller returns HTTP 409
     * when the submitted formVersion doesn't match the current DB version.
     *
     * COACTUPC oracle: re-reads the record under a CICS UPDATE lock;
     * any change from the snapshot causes "data changed" abort.
     *
     * Java implementation: @Version ensures the version column is managed by JPA.
     * The controller manually compares formVersion (from hidden form field) with
     * account.getVersion() from DB; mismatch → HTTP 409 Conflict.
     */
    @Test
    void rule053_versionFieldPresent() {
        long acctId = 30001L, custId = 9010L;
        seedAccountAndCustomer(acctId, custId);

        // Verify @Version field is initialized to 0 on first save
        var account = accountRepository.findById(acctId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(account.getVersion(),
            "RULE-053: @Version field must be initialized by JPA on first save");

        // Save (simulates a first update) — version increments to 1
        account.setActiveStatus("N");
        accountRepository.save(account);
        var updated = accountRepository.findById(acctId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(
            0L, updated.getVersion(),
            "RULE-053: @Version must be incremented after each save"
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rule053_controllerRejects_whenFormVersionStalerThanDb() throws Exception {
        long acctId = 40001L, custId = 9020L;
        seedAccountAndCustomer(acctId, custId);

        // Simulate: DB account has version=1 (as if another user updated it)
        var account = accountRepository.findById(acctId).orElseThrow();
        account.setActiveStatus("N"); // triggers increment → version=1
        accountRepository.save(account);

        // Now POST with version=0 (stale) → controller should return 409
        mockMvc.perform(post("/accounts/" + acctId + "/edit")
                .with(csrf())
                .param("version", "0")     // RULE-053: stale — DB is now at version=1
                .param("accountId", String.valueOf(acctId))
                .param("activeStatus", "Y")
                .param("creditLimit", "5000.00")
                .param("cashCreditLimit", "2500.00")
                .param("firstName", "Alice")
                .param("middleName", "M")
                .param("lastName", "Smith")
                .param("addrLine1", "Main Street")
                .param("addrStateCd", "WA")
                .param("addrZip", "98101")
                .param("addrCity", "Seattle")
                .param("addrCountryCd", "USA")
                .param("ficoCreditScore", "720")
                .param("eftAccountId", "1234567890")
                .param("priCardHolderInd", "Y"))
            .andExpect(status().isConflict()); // HTTP 409
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void seedAccountAndCustomer(long acctId, long custId) {
        accountRepository.save(new AccountEntity(
            acctId, "Y", new BigDecimal("1000.00"), new BigDecimal("5000.00"),
            new BigDecimal("2500.00"), "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "98101", "TESTGRP"
        ));
        customerRepository.save(new CustomerEntity(
            custId, "Alice", "M", "Smith",
            "123 Main St", "", "", "WA", "USA", "98101",
            "", "", 123456789L, "GOV001", "1990-01-01", "EFT0000001", "Y", 720
        ));
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", custId, acctId));
    }

    private void postValidUpdate(long acctId, long version) throws Exception {
        mockMvc.perform(post("/accounts/" + acctId + "/edit")
                .with(csrf())
                .param("accountId", String.valueOf(acctId))
                .param("version", String.valueOf(version))
                .param("activeStatus", "Y")
                .param("creditLimit", "5000.00")
                .param("cashCreditLimit", "2500.00")  // required by RULE-045
                .param("firstName", "Alice")
                .param("middleName", "M")
                .param("lastName", "Smith")
                .param("addrLine1", "Main Street")
                .param("addrLine2", "")
                .param("addrLine3", "")
                .param("addrStateCd", "WA")
                .param("addrZip", "98101")
                .param("addrCity", "Seattle")
                .param("addrCountryCd", "USA")
                .param("phoneNum1", "")
                .param("phoneNum2", "")
                .param("ficoCreditScore", "720")
                .param("eftAccountId", "1234567890")
                .param("priCardHolderInd", "Y"))
            .andExpect(status().is3xxRedirection()); // verify validation passes
    }
}
