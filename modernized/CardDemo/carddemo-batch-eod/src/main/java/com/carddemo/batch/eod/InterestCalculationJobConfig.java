package com.carddemo.batch.eod;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration for InterestCalculationJob.
 * Corresponds to CBACT04C.cbl (652 LOC): reads TCATBAL sequentially, computes monthly interest
 * per category, writes interest transactions, resets cycle accumulators.
 *
 * Uses a Tasklet step (not chunk-oriented) because CBACT04C groups TCATBAL records
 * by account ID during sequential processing — a stateful accumulation pattern that
 * does not fit Spring Batch's reader→processor→writer chunk model cleanly.
 */
@Configuration
public class InterestCalculationJobConfig {

    @Bean
    public Job interestCalculationJob(JobRepository jobRepository,
                                      Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", jobRepository)
            .start(interestCalculationStep)
            .build();
    }

    @Bean
    public Step interestCalculationStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        InterestCalculationTasklet tasklet) {
        return new StepBuilder("interestCalculationStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
