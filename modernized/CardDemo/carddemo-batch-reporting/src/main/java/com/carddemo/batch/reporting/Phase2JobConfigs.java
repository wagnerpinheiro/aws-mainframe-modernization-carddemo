package com.carddemo.batch.reporting;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch Job configurations for all Phase 2 reporting/migration programs.
 *
 * CBACT01C → accountListReportJob  (AccountReportTasklet)
 * CBACT02C → cardReportJob         (CardReportTasklet)
 * CBACT03C → crossRefReportJob     (CrossRefReportTasklet)
 * CBCUS01C → customerReportJob     (CustomerReportTasklet)
 * CBTRN03C → transactionReportJob  (TransactionReportTasklet) [RULE-021/022/023]
 * CBEXPORT → dataExportJob         (DataExportTasklet)
 * CBIMPORT → dataImportJob         (DataImportTasklet)        [SEC-014 Bean Validation]
 */
@Configuration
public class Phase2JobConfigs {

    // --- CBACT01C → accountListReportJob ---
    @Bean public Job accountListReportJob(JobRepository jr, Step accountListReportStep) {
        return new JobBuilder("accountListReportJob", jr).start(accountListReportStep).build();
    }
    @Bean public Step accountListReportStep(JobRepository jr, PlatformTransactionManager tm, AccountReportTasklet t) {
        return new StepBuilder("accountListReportStep", jr).tasklet(t, tm).build();
    }

    // --- CBACT02C → cardReportJob ---
    @Bean public Job cardReportJob(JobRepository jr, Step cardReportStep) {
        return new JobBuilder("cardReportJob", jr).start(cardReportStep).build();
    }
    @Bean public Step cardReportStep(JobRepository jr, PlatformTransactionManager tm, CardReportTasklet t) {
        return new StepBuilder("cardReportStep", jr).tasklet(t, tm).build();
    }

    // --- CBACT03C → crossRefReportJob ---
    @Bean public Job crossRefReportJob(JobRepository jr, Step crossRefReportStep) {
        return new JobBuilder("crossRefReportJob", jr).start(crossRefReportStep).build();
    }
    @Bean public Step crossRefReportStep(JobRepository jr, PlatformTransactionManager tm, CrossRefReportTasklet t) {
        return new StepBuilder("crossRefReportStep", jr).tasklet(t, tm).build();
    }

    // --- CBCUS01C → customerReportJob ---
    @Bean public Job customerReportJob(JobRepository jr, Step customerReportStep) {
        return new JobBuilder("customerReportJob", jr).start(customerReportStep).build();
    }
    @Bean public Step customerReportStep(JobRepository jr, PlatformTransactionManager tm, CustomerReportTasklet t) {
        return new StepBuilder("customerReportStep", jr).tasklet(t, tm).build();
    }

    // --- CBTRN03C → transactionReportJob ---
    @Bean public Job transactionReportJob(JobRepository jr, Step transactionReportStep) {
        return new JobBuilder("transactionReportJob", jr).start(transactionReportStep).build();
    }
    @Bean public Step transactionReportStep(JobRepository jr, PlatformTransactionManager tm, TransactionReportTasklet t) {
        return new StepBuilder("transactionReportStep", jr).tasklet(t, tm).build();
    }

    // --- CBEXPORT → dataExportJob ---
    @Bean public Job dataExportJob(JobRepository jr, Step dataExportStep) {
        return new JobBuilder("dataExportJob", jr).start(dataExportStep).build();
    }
    @Bean public Step dataExportStep(JobRepository jr, PlatformTransactionManager tm, DataExportTasklet t) {
        return new StepBuilder("dataExportStep", jr).tasklet(t, tm).build();
    }

    // --- CBIMPORT → dataImportJob ---
    @Bean public Job dataImportJob(JobRepository jr, Step dataImportStep) {
        return new JobBuilder("dataImportJob", jr).start(dataImportStep).build();
    }
    @Bean public Step dataImportStep(JobRepository jr, PlatformTransactionManager tm, DataImportTasklet t) {
        return new StepBuilder("dataImportStep", jr).tasklet(t, tm).build();
    }
}
