package com.carddemo.batch.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.carddemo.domain.entity")
@EnableJpaRepositories("com.carddemo.domain.repository")
public class CardDemoReportingApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardDemoReportingApplication.class, args);
    }
}
