package com.carddemo.batch.eod;

import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the production {@link TransactionValidationJobConfig#dailyTranReader} factory
 * directly — without the {@link TransactionValidationJobTest.OverridableReaderConfig} bean
 * override that shadows the production bean in every @SpringBatchTest context.
 *
 * <h2>Why this test exists (HIGH-2)</h2>
 * {@link TransactionValidationJobTest} loads {@code OverridableReaderConfig}, which registers
 * a {@code @StepScope @Primary} bean named {@code "dailyTranReader"} that completely replaces
 * the production reader.  All five characterization tests therefore exercise the test-config
 * column ranges — <em>not</em> the column ranges in {@link TransactionValidationJobConfig}.
 * A one-position offset in any production column range (e.g., {@code Range(263,279)} instead
 * of {@code Range(263,278)} for {@code cardNumber}) would silently pass all five tests but
 * corrupt every XREF lookup in production.
 *
 * <h2>Oracle</h2>
 * Expected values are derived from byte-level inspection of
 * {@code src/test/resources/fixtures/dailytran.txt}, line 1, against the CVTRA06Y COBOL
 * copybook layout.  They are intentionally hardcoded — if production column ranges drift,
 * these assertions will catch the regression.
 */
class TransactionValidationReaderTest {

    /**
     * Calls the production factory method directly (no Spring context).
     * This instantiates the same {@link org.springframework.batch.item.file.transform.FixedLengthTokenizer}
     * and {@code fieldSetMapper} lambda that run in production, pinning column ranges
     * against known byte positions in the DALYTRAN fixture file.
     */
    @Test
    void productionReader_parsesFirstRecord_columnRangesAreCorrect() throws Exception {
        // Instantiate the production config and call the @Bean factory method directly.
        // @Value injection is bypassed — we pass the resource explicitly, as production
        // would receive it from the Spring environment.
        var config = new TransactionValidationJobConfig();
        FlatFileItemReader<DailyTransactionRecord> reader =
            config.dailyTranReader(new ClassPathResource("fixtures/dailytran.txt"));

        reader.open(new ExecutionContext());
        try {
            DailyTransactionRecord first = reader.read();

            assertThat(first).as("first record must not be null").isNotNull();

            // --- transactionId: CVTRA06Y bytes 1–16 ---
            // Fixture byte 0–15: "0000000000683580"
            assertThat(first.transactionId())
                .as("transactionId must be parsed from bytes 1–16 (Range 1,16)")
                .isEqualTo("0000000000683580");

            // --- typeCode: bytes 17–18 ---
            assertThat(first.typeCode())
                .as("typeCode must be parsed from bytes 17–18 (Range 17,18)")
                .isEqualTo("01");

            // --- categoryCode: bytes 19–22 ---
            assertThat(first.categoryCode())
                .as("categoryCode must be parsed from bytes 19–22 (Range 19,22)")
                .isEqualTo(1);

            // --- amount: bytes 133–143, S9(9)V99 sign-overpunch ---
            // Fixture raw: "0000005047G"  →  last char 'G' = positive digit 7
            // → digits "00000050477" → BigDecimal / 100 = 504.77
            assertThat(first.amount())
                .as("amount must be parsed and sign-decoded from bytes 133–143 (Range 133,143)")
                .isEqualByComparingTo(new BigDecimal("504.77"));

            // --- merchantId: bytes 144–152 ---
            assertThat(first.merchantId())
                .as("merchantId must be parsed from bytes 144–152 (Range 144,152)")
                .isEqualTo(800000000L);

            // --- cardNumber: bytes 263–278 — THE XREF LOOKUP KEY ---
            // This is the most critical column: an off-by-one here causes every XREF
            // lookup to use a wrong key, producing CARD_NOT_FOUND for all records.
            assertThat(first.cardNumber())
                .as("cardNumber must be parsed from bytes 263–278 (Range 263,278)")
                .isEqualTo("4859452612877065");

        } finally {
            reader.close();
        }
    }

    /**
     * Verifies that the reader processes all 300 fixture records without throwing,
     * confirming that the tokenizer tolerates the full record population correctly.
     */
    @Test
    void productionReader_reads300Records() throws Exception {
        var config = new TransactionValidationJobConfig();
        FlatFileItemReader<DailyTransactionRecord> reader =
            config.dailyTranReader(new ClassPathResource("fixtures/dailytran.txt"));

        reader.open(new ExecutionContext());
        try {
            int count = 0;
            while (reader.read() != null) {
                count++;
            }
            assertThat(count)
                .as("dailytran.txt must yield exactly 300 records")
                .isEqualTo(300);
        } finally {
            reader.close();
        }
    }
}
