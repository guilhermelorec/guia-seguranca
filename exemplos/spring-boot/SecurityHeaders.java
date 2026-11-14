package com.exemplo.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

/**
 * API REST stateless com tudo fechado por padrão e security headers (guia, itens 5, 15 e 16).
 * Spring Security 6.4+/7.
 */
@Configuration
public class SecurityHeaders {

    private static final String[] PUBLICOS = { "/auth/login", "/auth/refresh", "/actuator/health" };

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)            // só sem cookie de autenticação (token no header)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLICOS).permitAll()
                        .anyRequest().authenticated())               // tudo fechado por padrão
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        // Atrás de proxy TLS o request chega como http: envie HSTS sempre.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)
                                .requestMatcher(AnyRequestMatcher.INSTANCE)))
                .build();
    }
}

/*
 * application.yml de produção:
 *
 * server:
 *   error: { include-stacktrace: never, include-message: never, include-binding-errors: never }
 * spring:
 *   servlet.multipart: { max-file-size: 1MB, max-request-size: 2MB }
 * springdoc: { api-docs.enabled: false, swagger-ui.enabled: false }
 * management.endpoint.health.show-details: never
 */
