package com.carddemo.web.card;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for COCRDLIC + COCRDSLC + COCRDUPC → Card controllers.
 *
 * SEC-005: CVV field must never appear in any response.
 * RULE-027/060: card active status Y or N.
 * RULE-028: expiry month 1–12.
 * RULE-029: expiry year 1950–2099.
 * RULE-030: expiry day carried forward (not editable).
 * RULE-031: card must exist before detail is shown.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired CardRepository cardRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired CardXRefRepository cardXRefRepository;

    @BeforeEach
    void setUp() {
        cardXRefRepository.deleteAll();
        cardRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // =========================================================================
    // COCRDSLC — card detail (read-only)
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void cardDetail_displaysCard() throws Exception {
        seedCard("4111111111111111", 10001L, "ALICE SMITH", "2028-06-15", "Y");

        mockMvc.perform(get("/cards/4111111111111111"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("4111111111111111")))
            .andExpect(content().string(containsString("ALICE SMITH")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void cardDetail_notFound_showsError() throws Exception {
        mockMvc.perform(get("/cards/9999999999999999"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("not found")));
    }

    // =========================================================================
    // COCRDLIC — card list for account
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void cardList_showsCardsForAccount() throws Exception {
        seedAccount(20001L);
        seedCard("4111111111111111", 20001L, "BOB JONES", "2028-06-15", "Y");
        seedCard("4222222222222222", 20001L, "BOB JONES", "2029-03-20", "N");

        mockMvc.perform(get("/accounts/20001/cards"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("4111111111111111")))
            .andExpect(content().string(containsString("4222222222222222")));
    }

    // =========================================================================
    // COCRDUPC — card update validation
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCard_invalidMonth_failsRule028() throws Exception {
        seedCard("4333333333333333", 30001L, "CAROL BROWN", "2028-06-15", "Y");

        mockMvc.perform(post("/cards/4333333333333333/edit")
                .with(csrf())
                .param("cardNumber", "4333333333333333")
                .param("embossedName", "CAROL BROWN")
                .param("activeStatus", "Y")
                .param("expiryMonth", "13")   // RULE-028: invalid
                .param("expiryYear", "2028"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("RULE-028")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCard_invalidActiveStatus_failsRule027() throws Exception {
        seedCard("4444444444444444", 40001L, "DAVE LEE", "2028-06-15", "Y");

        mockMvc.perform(post("/cards/4444444444444444/edit")
                .with(csrf())
                .param("cardNumber", "4444444444444444")
                .param("embossedName", "DAVE LEE")
                .param("activeStatus", "X")   // RULE-027: must be Y or N
                .param("expiryMonth", "6")
                .param("expiryYear", "2028"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("RULE-027")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCard_validUpdate_dayCarriedForward() throws Exception {
        // RULE-030: day=15 from original record must be preserved
        seedCard("4555555555555555", 50001L, "EVE MARTIN", "2028-06-15", "Y");

        mockMvc.perform(post("/cards/4555555555555555/edit")
                .with(csrf())
                .param("cardNumber", "4555555555555555")
                .param("embossedName", "EVE MARTINEZ")
                .param("activeStatus", "Y")
                .param("expiryMonth", "12")
                .param("expiryYear", "2030"))
            .andExpect(status().is3xxRedirection());

        // Reload and verify day was preserved (RULE-030)
        var updated = cardRepository.findById("4555555555555555").orElseThrow();
        assert updated.getExpirationDate().endsWith("-15") : "Day must be carried forward: " + updated.getExpirationDate();
        assert updated.getEmbossedName().equals("EVE MARTINEZ");
    }

    // =========================================================================
    // SEC-005: CVV must never appear
    // =========================================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    void cardDetail_noCvvInResponse() throws Exception {
        seedCard("4666666666666666", 60001L, "FRANK WILSON", "2028-06-15", "Y");

        mockMvc.perform(get("/cards/4666666666666666"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("cvv"))))
            .andExpect(content().string(not(containsString("CVV"))))
            .andExpect(content().string(not(containsString("CARD_CVV"))));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void seedAccount(long acctId) {
        accountRepository.save(new AccountEntity(
            acctId, "Y", BigDecimal.ZERO, new BigDecimal("5000"), new BigDecimal("2500"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "98101", "GRPA"
        ));
    }

    private void seedCard(String cardNumber, long accountId, String name, String expiry, String status) {
        if (!accountRepository.existsById(accountId)) {
            seedAccount(accountId);
        }
        cardRepository.save(new CardEntity(cardNumber, accountId, name, expiry, status));
    }
}
