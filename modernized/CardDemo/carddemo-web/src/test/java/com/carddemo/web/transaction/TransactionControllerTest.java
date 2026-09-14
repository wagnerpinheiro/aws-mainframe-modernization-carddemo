package com.carddemo.web.transaction;

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
 * Characterization tests for COTRN00-02C → Transaction controllers.
 *
 * RULE-047–054: transaction add validation.
 * RULE-051: two-step confirmation (Y required).
 * RULE-012: unique IDs from sequence.
 * RULE-054: Q11 — strict date parsing, no error 2513 suppression.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionControllerTest {

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

    /** GET /transactions/add shows the entry form. */
    @Test
    @WithMockUser
    void addForm_showsPage() throws Exception {
        mvc.perform(get("/transactions/add"))
            .andExpect(status().isOk())
            .andExpect(view().name("transactions/add"));
    }

    /** RULE-047: missing card number → validation error, no transaction saved. */
    @Test
    @WithMockUser
    void rule047_blankCardNumber_validationError() throws Exception {
        mvc.perform(post("/transactions/add")
                .param("cardNumber", "")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "50.00")
                .param("description", "Test")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("transactions/add"))
            .andExpect(model().attributeExists("error"));

        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    /** RULE-050: zero amount → validation error. */
    @Test
    @WithMockUser
    void rule050_zeroAmount_validationError() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("5001000500010001", 9005L, 50001L));

        mvc.perform(post("/transactions/add")
                .param("cardNumber", "5001000500010001")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "0")
                .param("description", "Test")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("transactions/add"));

        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    /** RULE-054 (Q11): invalid date format → validation error. */
    @Test
    @WithMockUser
    void rule054_invalidDateFormat_validationError() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("6001000600010001", 9006L, 60001L));

        mvc.perform(post("/transactions/add")
                .param("cardNumber", "6001000600010001")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "10.00")
                .param("description", "Test")
                .param("originDate", "13/25/2024") // invalid
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("transactions/add"))
            .andExpect(model().attributeExists("error"));
    }

    /** RULE-051: POST /transactions/add with valid data shows confirmation page. */
    @Test
    @WithMockUser
    void rule051_validInput_showsConfirmationPage() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("7001000700010001", 9007L, 70001L));

        mvc.perform(post("/transactions/add")
                .param("cardNumber", "7001000700010001")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "25.00")
                .param("description", "Grocery purchase")
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(view().name("transactions/confirm"));
    }

    /** RULE-051: POST /transactions/add/confirm with confirmed=N → back to form. */
    @Test
    @WithMockUser
    void rule051_confirmNo_returnToForm() throws Exception {
        mvc.perform(post("/transactions/add/confirm")
                .param("cardNumber", "8001000800010001")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "10.00")
                .param("description", "Test")
                .param("confirmed", "N")
                .with(csrf()))
            .andExpect(view().name("transactions/add"));
    }

    /** RULE-052: blank description → validation error. */
    @Test
    @WithMockUser
    void rule052_blankDescription_validationError() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("9001000900010001", 9009L, 90001L));

        mvc.perform(post("/transactions/add")
                .param("cardNumber", "9001000900010001")
                .param("typeCode", "01")
                .param("categoryCode", "1")
                .param("amount", "5.00")
                .param("description", "")
                .with(csrf()))
            .andExpect(view().name("transactions/add"))
            .andExpect(model().attributeExists("error"));
    }
}
