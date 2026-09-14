package com.carddemo.web.auth;

import com.carddemo.domain.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads UserEntity from the database and converts it to Spring Security's UserDetails.
 *
 * Replicates COSGN00C READ-USER-SEC-FILE (EXEC CICS READ DATASET('USRSEC')):
 * - userId not found → UsernameNotFoundException (RESP=13 → "User not found")
 * - userType 'A' → ROLE_ADMIN (CDEMO-USRTYP-ADMIN → COADM01C)
 * - userType 'U' → ROLE_USER  (CDEMO-USRTYP-USER  → COMEN01C)
 *
 * SEC-003 fix: Spring Security uses BCryptPasswordEncoder.matches() instead of
 * the COBOL plaintext comparison (SEC-USR-PWD = WS-USER-PWD).
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        var user = userRepository.findById(userId.toUpperCase())
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + userId));

        String role = "A".equals(user.getUserType()) ? "ROLE_ADMIN" : "ROLE_USER";
        return new User(user.getUserId(), user.getPassword(),
            List.of(new SimpleGrantedAuthority(role)));
    }
}
