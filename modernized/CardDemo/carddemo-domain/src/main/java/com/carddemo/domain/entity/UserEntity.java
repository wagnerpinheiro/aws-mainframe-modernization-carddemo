package com.carddemo.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps SEC-USER-DATA (CSUSR01Y.cpy) / USRSEC VSAM KSDS. Record length: 80 bytes.
 *
 * SEC-003 fix: password is stored as BCrypt hash, NOT the plaintext SEC-USR-PWD
 * field from the COBOL. The COBOL compared SEC-USR-PWD == WS-USER-PWD in plaintext;
 * Java replaces this with BCryptPasswordEncoder.matches().
 */
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    @Column(name = "user_id", length = 8)
    private String userId;

    @Column(name = "first_name", length = 20)
    private String firstName;

    @Column(name = "last_name", length = 20)
    private String lastName;

    /** BCrypt hash — never the plaintext value from legacy SEC-USR-PWD. */
    @Column(name = "password", length = 60)
    private String password;

    /** 'A' = admin (CDEMO-USRTYP-ADMIN), 'U' = regular user (CDEMO-USRTYP-USER). */
    @Column(name = "user_type", length = 1)
    private String userType;

    protected UserEntity() {}

    public UserEntity(String userId, String firstName, String lastName,
                      String password, String userType) {
        this.userId = userId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.userType = userType;
    }

    public String getUserId() { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPassword() { return password; }
    public String getUserType() { return userType; }

    public boolean isAdmin() { return "A".equals(userType); }
}
