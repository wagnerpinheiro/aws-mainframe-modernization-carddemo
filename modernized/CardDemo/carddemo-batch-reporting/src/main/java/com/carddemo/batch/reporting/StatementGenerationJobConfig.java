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
 * Spring Batch configuration for StatementGenerationJob.
 * Corresponds to CBSTM03A.cbl (924 LOC) + CBSTM03B.cbl (230 LOC).
 *
 * Single Tasklet step — account-centric, stateful processing that reads XREF sequentially
 * and looks up customer, account, and transactions per card number.
 * Not chunk-oriented: the output is formatted text/HTML per account, not per item.
 */
@Configuration
public class StatementGenerationJobConfig {

    @Bean
    public Job statementGenerationJob(JobRepository jobRepository,
                                      Step statementGenerationStep) {
        return new JobBuilder("statementGenerationJob", jobRepository)
            .start(statementGenerationStep)
            .build();
    }

    @Bean
    public Step statementGenerationStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        StatementGenerationTasklet tasklet) {
        return new StepBuilder("statementGenerationStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
