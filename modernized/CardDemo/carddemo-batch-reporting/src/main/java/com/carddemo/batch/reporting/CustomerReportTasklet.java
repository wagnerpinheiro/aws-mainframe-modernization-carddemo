package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads all customers and writes a report.
 * Corresponds to CBCUS01C.cbl: reads and prints customer data file.
 * SSN is not included in the report output (security).
 */
@Component
public class CustomerReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CustomerReportTasklet.class);
    private static final String SEP = "-".repeat(80);

    private final CustomerRepository customerRepository;
    private final String outputPath;

    public CustomerReportTasklet(CustomerRepository customerRepository,
            @Value("${carddemo.reporting.customer-report-output}") String outputPath) {
        this.customerRepository = customerRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<CustomerEntity> customers = customerRepository.findAllByOrderByIdAsc();
        if (Path.of(outputPath).getParent() != null)
            Files.createDirectories(Path.of(outputPath).getParent());

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            w.write("CUSTOMER DATA REPORT"); w.newLine();
            w.write(SEP); w.newLine();
            for (CustomerEntity c : customers) {
                // SSN omitted from report
                w.write(String.format("CUST-ID: %09d  NAME: %-25s %-25s %-25s  FICO: %d",
                        c.getId(),
                        c.getFirstName() != null ? c.getFirstName().strip() : "",
                        c.getMiddleName() != null ? c.getMiddleName().strip() : "",
                        c.getLastName() != null ? c.getLastName().strip() : "",
                        c.getFicoCreditScore() != null ? c.getFicoCreditScore() : 0));
                w.newLine();
                w.write(String.format("         ADDR: %s, %s, %s %s %s",
                        c.getAddrLine1() != null ? c.getAddrLine1().strip() : "",
                        c.getAddrLine3() != null ? c.getAddrLine3().strip() : "",
                        c.getAddrStateCd() != null ? c.getAddrStateCd().strip() : "",
                        c.getAddrCountryCd() != null ? c.getAddrCountryCd().strip() : "",
                        c.getAddrZip() != null ? c.getAddrZip().strip() : ""));
                w.newLine();
            }
            w.write(SEP); w.newLine();
            w.write(String.format("TOTAL CUSTOMERS: %d", customers.size())); w.newLine();
        }
        log.info("CustomerReport: wrote {} customers to {}", customers.size(), outputPath);
        return RepeatStatus.FINISHED;
    }
}
