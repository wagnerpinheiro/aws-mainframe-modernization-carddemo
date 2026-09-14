package com.carddemo.web;

import com.carddemo.domain.entity.*;
import com.carddemo.domain.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds demo data on startup so the app is usable without a real VSAM migration.
 * Active in all profiles. Safe to re-run: deletes and re-seeds on every start.
 *
 * Demo credentials:
 *   ADMIN:  userId=ADMN0001  password=Admin123
 *   USER:   userId=USER0001  password=User1234
 */
@Component
public class DemoDataInitializer implements ApplicationRunner {

    private final UserRepository userRepo;
    private final CustomerRepository customerRepo;
    private final AccountRepository accountRepo;
    private final CardRepository cardRepo;
    private final CardXRefRepository cardXRefRepo;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(UserRepository userRepo,
                               CustomerRepository customerRepo,
                               AccountRepository accountRepo,
                               CardRepository cardRepo,
                               CardXRefRepository cardXRefRepo,
                               PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.customerRepo = customerRepo;
        this.accountRepo = accountRepo;
        this.cardRepo = cardRepo;
        this.cardXRefRepo = cardXRefRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepo.deleteAll();
        cardXRefRepo.deleteAll();
        cardRepo.deleteAll();
        accountRepo.deleteAll();
        customerRepo.deleteAll();

        // Users
        userRepo.save(new UserEntity("ADMN0001", "Admin", "Demo",
            passwordEncoder.encode("Admin123"), "A"));
        userRepo.save(new UserEntity("USER0001", "Alice", "Smith",
            passwordEncoder.encode("User1234"), "U"));

        // Customer 1 — Alice Smith
        customerRepo.save(new CustomerEntity(
            1001L, "Alice", "M", "Smith",
            "123 Main Street", "Apt 4B", "", "CA", "USA", "90210",
            "310-555-0101", "310-555-0202",
            987654321L, "DL-CA-1234567", "1985-03-15",
            "EFT0000001", "Y", 720
        ));

        // Account 1 — Alice Smith
        accountRepo.save(new AccountEntity(
            100001L, "Y",
            new BigDecimal("1250.75"),   // current balance
            new BigDecimal("5000.00"),   // credit limit
            new BigDecimal("2500.00"),   // cash credit limit
            "2024-01-01",                // open date
            "2027-12-31",                // expiration date
            "2025-01-01",                // reissue date
            new BigDecimal("500.00"),    // cycle credit
            new BigDecimal("1750.75"),   // cycle debit
            "90210",                     // group id
            "SILVER"                     // discount group
        ));

        // Card 1 — Alice Smith
        cardRepo.save(new CardEntity(
            "4111111111111111", 100001L, "ALICE M SMITH", "2027-12-31", "Y"
        ));
        cardXRefRepo.save(new CardXRefEntity("4111111111111111", 1001L, 100001L));

        // Customer 2 — Bob Jones
        customerRepo.save(new CustomerEntity(
            1002L, "Bob", "", "Jones",
            "456 Oak Avenue", "", "", "TX", "USA", "78701",
            "512-555-0303", "",
            123456789L, "DL-TX-7654321", "1978-07-22",
            "EFT0000002", "Y", 680
        ));

        // Account 2 — Bob Jones
        accountRepo.save(new AccountEntity(
            100002L, "Y",
            new BigDecimal("320.00"),
            new BigDecimal("3000.00"),
            new BigDecimal("1500.00"),
            "2023-06-01",
            "2026-06-30",
            "2024-06-01",
            new BigDecimal("0.00"),
            new BigDecimal("320.00"),
            "78701",
            "BASIC"
        ));

        // Card 2 — Bob Jones
        cardRepo.save(new CardEntity(
            "4222222222222222", 100002L, "BOB JONES", "2026-06-30", "Y"
        ));
        cardXRefRepo.save(new CardXRefEntity("4222222222222222", 1002L, 100002L));

        // Account 3 — inactive (for testing RULE-032)
        customerRepo.save(new CustomerEntity(
            1003L, "Carol", "", "Brown",
            "789 Pine Road", "", "", "NY", "USA", "10001",
            "212-555-0404", "",
            111222333L, "DL-NY-9876543", "1990-11-30",
            "EFT0000003", "Y", 590
        ));
        accountRepo.save(new AccountEntity(
            100003L, "N",   // inactive
            new BigDecimal("0.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("500.00"),
            "2020-01-01",
            "2025-01-31",
            "2023-01-01",
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            "10001",
            "BASIC"
        ));
        cardRepo.save(new CardEntity(
            "4333333333333333", 100003L, "CAROL BROWN", "2025-01-31", "N"
        ));
        cardXRefRepo.save(new CardXRefEntity("4333333333333333", 1003L, 100003L));

        System.out.println("""
            ╔══════════════════════════════════════════════════╗
            ║         CardDemo — Demo Data Loaded              ║
            ╠══════════════════════════════════════════════════╣
            ║  ADMIN  userId=ADMN0001  password=Admin123       ║
            ║  USER   userId=USER0001  password=User1234       ║
            ╠══════════════════════════════════════════════════╣
            ║  Accounts: 100001 (Alice, $1250.75 balance)      ║
            ║            100002 (Bob,   $320.00  balance)      ║
            ║            100003 (Carol, inactive)              ║
            ║  Cards:    4111111111111111  4222222222222222     ║
            ╚══════════════════════════════════════════════════╝
            """);
    }
}
