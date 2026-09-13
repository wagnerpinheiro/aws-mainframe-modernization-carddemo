package com.carddemo.batch.eod;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import com.carddemo.common.CobolDisplayParser;

/**
 * Spring Batch configuration for TransactionValidationJob.
 * Corresponds to CBTRN01C.cbl (494 LOC, reads DALYTRAN, validates XREF + ACCOUNT).
 */
@Configuration
public class TransactionValidationJobConfig {

    @Bean
    public Job transactionValidationJob(JobRepository jobRepository, Step transactionValidationStep) {
        return new JobBuilder("transactionValidationJob", jobRepository)
            .start(transactionValidationStep)
            .build();
    }

    @Bean
    public Step transactionValidationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<DailyTransactionRecord> dailyTranReader,
            TransactionValidationProcessor processor,
            TransactionValidationWriter writer) {
        return new StepBuilder("transactionValidationStep", jobRepository)
            .<DailyTransactionRecord, TransactionValidationResult>chunk(100, transactionManager)
            .reader(dailyTranReader)
            .processor(processor)
            .writer(writer)
            .build();
    }

    /**
     * Reads DALYTRAN fixed-width records (350 bytes per CVTRA06Y).
     *
     * COBOL: SELECT DALYTRAN-FILE ASSIGN TO DALYTRAN ORGANIZATION IS SEQUENTIAL.
     */
    @Bean
    public FlatFileItemReader<DailyTransactionRecord> dailyTranReader(
            @Value("${carddemo.batch.dalytran-input}") Resource inputResource) {

        var tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(
            "transactionId", "typeCode", "categoryCode", "source", "description",
            "amount", "merchantId", "merchantName", "merchantCity", "merchantZip",
            "cardNumber", "originTs", "processTs"
        );
        tokenizer.setColumns(
            new Range(1, 16),   // DALYTRAN-ID
            new Range(17, 18),  // DALYTRAN-TYPE-CD
            new Range(19, 22),  // DALYTRAN-CAT-CD
            new Range(23, 32),  // DALYTRAN-SOURCE
            new Range(33, 132), // DALYTRAN-DESC
            new Range(133, 143),// DALYTRAN-AMT   S9(9)V99 sign-overpunched
            new Range(144, 152),// DALYTRAN-MERCHANT-ID
            new Range(153, 202),// DALYTRAN-MERCHANT-NAME
            new Range(203, 252),// DALYTRAN-MERCHANT-CITY
            new Range(253, 262),// DALYTRAN-MERCHANT-ZIP
            new Range(263, 278),// DALYTRAN-CARD-NUM
            new Range(279, 304),// DALYTRAN-ORIG-TS
            new Range(305, 330) // DALYTRAN-PROC-TS
        );
        tokenizer.setStrict(false); // tolerate shorter lines (missing filler)

        return new FlatFileItemReaderBuilder<DailyTransactionRecord>()
            .name("dailyTranReader")
            .resource(inputResource)
            .lineTokenizer(tokenizer)
            .fieldSetMapper(fieldSet -> new DailyTransactionRecord(
                fieldSet.readString("transactionId").trim(),
                fieldSet.readString("typeCode").trim(),
                Integer.parseInt(fieldSet.readString("categoryCode").trim()),
                fieldSet.readString("source").trim(),
                fieldSet.readString("description").trim(),
                CobolDisplayParser.parseSignedAmount(fieldSet.readString("amount"), 2),
                Long.parseLong(fieldSet.readString("merchantId").trim()),
                fieldSet.readString("merchantName").trim(),
                fieldSet.readString("merchantCity").trim(),
                fieldSet.readString("merchantZip").trim(),
                fieldSet.readString("cardNumber").trim(),
                fieldSet.readString("originTs").trim(),
                fieldSet.readString("processTs").trim()
            ))
            .build();
    }
}
