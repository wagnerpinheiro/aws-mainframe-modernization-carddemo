package com.carddemo.web.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/**
 * Spring Security 6 configuration.
 *
 * Replaces COSGN00C authentication logic + CICS XCTL dispatch:
 * - RULE-001: blank userId → UsernameNotFoundException → /login?error
 * - RULE-002: blank password → BadCredentialsException → /login?error
 * - RULE-003: unknown userId → UsernameNotFoundException → /login?error
 * - RULE-004: wrong password → BadCredentialsException → /login?error (BCrypt replaces plaintext — SEC-003)
 * - RULE-005: admin → redirect to /admin/menu (was XCTL COADM01C)
 * - RULE-006: regular user cannot access /admin/** → 403
 * - RULE-055: logout invalidates session
 *
 * SEC-009 fix: /admin/users/** requires ROLE_ADMIN (controllers also carry @PreAuthorize).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/login?error", "/login?logout").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler())
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)      // RULE-055: session lifecycle
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
        return http.build();
    }

    /**
     * Routes authenticated users by role (replicates COSGN00C XCTL dispatch):
     * admin → /admin/menu (was XCTL COADM01C)
     * user  → /menu       (was XCTL COMEN01C)
     */
    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (HttpServletRequest req, HttpServletResponse res, Authentication auth) -> {
            boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            res.sendRedirect(isAdmin ? "/admin/menu" : "/menu");
        };
    }
}
