package com.imara.banque.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Configuration Spring Security.
 *
 * Équivalent de :
 *  - django.contrib.auth (authentification)
 *  - django.middleware.csrf.CsrfViewMiddleware
 *  - @login_required (accès restreint aux routes)
 *
 * Note : l'authentification en 2 étapes (username puis password)
 * est gérée manuellement dans AuthController, pas via le formulaire
 * standard de Spring Security — même logique que Django.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── Autorisation des routes ─────────────────────────────────
            // Équivalent des @login_required Django + urls.py publiques
            .authorizeHttpRequests(auth -> auth
                // Routes publiques (équivalent des vues sans @login_required)
                .requestMatchers(
                    "/",
                    "/auth/**",          // login étape 1 & 2, blocage
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/static/**",
                    "/error"
                ).permitAll()
                // Tout le reste nécessite d'être authentifié
                .anyRequest().authenticated()
            )

            // ── Formulaire de login personnalisé ───────────────────────
            // Spring gère la session ; notre AuthController fait la logique
            // métier (blocage, journal, alerte).
            .formLogin(form -> form
                .loginPage("/auth/")
                .defaultSuccessUrl("/dashboard/", true)
                .failureUrl("/auth/?error")
                .permitAll()
            )

            // ── Déconnexion ─────────────────────────────────────────────
            // Équivalent de logout() Django + redirect vers étape1
            .logout(logout -> logout
                .logoutUrl("/auth/deconnexion/")
                .logoutSuccessUrl("/auth/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )

            // ── CSRF ────────────────────────────────────────────────────
            // Équivalent de CsrfViewMiddleware Django
            // CookieCsrfTokenRepository permet l'usage en AJAX (refresh-session)
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )

            // ── Gestion de session ──────────────────────────────────────
            .sessionManagement(session -> session
                .maximumSessions(5)            // max sessions simultanées par user
                .expiredUrl("/auth/?expired")
            );

        return http.build();
    }
}
