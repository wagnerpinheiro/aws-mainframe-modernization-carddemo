package com.carddemo.web.billing;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Characterization tests for COBIL00C → BillPaymentController + BillPaymentService.
 *
 * RULE-010: payment amount = full current balance.
 * RULE-011: zero or negative balance → payment blocked.
 * RULE-012: unique transaction IDs (DB sequence, not READPREV+increment).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

    @Autowired MockMvc mvc;
    @Autowired AccountRepository accountRepository;
    @Autowired CardXRefRepository cardXRefRepository;
    @Autowired TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();
    }

    /** RULE-010: full-balance payment saves TransactionEntity and zeroes account balance. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void rule010_fullBalancePayment_savesTransactionAndZeroesBalance() throws Exception {
        long acctId = 10001L;
        accountRepository.save(account(acctId, "500.00"));
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", 9001L, acctId));

        mvc.perform(post("/billing/pay")
                .param("accountId", String.valueOf(acctId))
                .param("confirmed", "Y")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/billing"));

        // RULE-010: account balance = 0
        AccountEntity updated = accountRepository.findById(acctId).orElseThrow();
        assertThat(updated.getCurrentBalance()).isEqualByComparingTo("0.00");

        // Transaction saved with correct fields
        assertThat(transactionRepository.count()).isEqualTo(1);
        var tran = transactionRepository.findAll().get(0);
        assertThat(tran.getAmount()).isEqualByComparingTo("500.00");
        assertThat(tran.getTypeCode()).isEqualTo("02");
        assertThat(tran.getDescription()).contains("BILL PAYMENT");
    }

    /** RULE-011: zero-balance account → payment rejected, no transaction saved. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void rule011_zeroBalance_paymentBlocked() throws Exception {
        long acctId = 20001L;
        accountRepository.save(account(acctId, "0.00"));
        cardXRefRepository.save(new CardXRefEntity("2001000200010001", 9002L, acctId));

        mvc.perform(post("/billing/pay")
                .param("accountId", String.valueOf(acctId))
                .param("confirmed", "Y")
                .with(csrf()))
            .andExpect(status().isOk()) // form re-displayed with error
            .andExpect(view().name("billing/pay"));

        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    /** RULE-012: two sequential payments produce different transaction IDs (no READPREV race). */
    @Test
    @WithMockUser(roles = "ADMIN")
    void rule012_sequentialPayments_uniqueTransactionIds() throws Exception {
        long acctId1 = 30001L, acctId2 = 30002L;
        accountRepository.save(account(acctId1, "100.00"));
        accountRepository.save(account(acctId2, "200.00"));
        cardXRefRepository.save(new CardXRefEntity("3001000300010001", 9003L, acctId1));
        cardXRefRepository.save(new CardXRefEntity("3002000300020002", 9004L, acctId2));

        mvc.perform(post("/billing/pay")
                .param("accountId", String.valueOf(acctId1))
                .param("confirmed", "Y").with(csrf()));
        mvc.perform(post("/billing/pay")
                .param("accountId", String.valueOf(acctId2))
                .param("confirmed", "Y").with(csrf()));

        var ids = transactionRepository.findAll().stream().map(t -> t.getId()).toList();
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    /** Confirmation step shows account balance before committing. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void confirmationStep_showsBalance() throws Exception {
        long acctId = 40001L;
        accountRepository.save(account(acctId, "750.00"));

        mvc.perform(post("/billing/confirm")
                .param("accountId", String.valueOf(acctId))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("billing/confirm"))
            .andExpect(model().attributeExists("account"));
    }

    private AccountEntity account(long id, String balance) {
        return new AccountEntity(id, "Y", new BigDecimal(balance),
            new BigDecimal("5000.00"), new BigDecimal("2500.00"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "00000", "TESTGRP");
    }
}
