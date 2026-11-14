package com.exemplo.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Rate limit por IP nos endpoints sensíveis (guia, item 11). Registre na cadeia do Spring Security:
 *   http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
 *
 * Usa getRemoteAddr(), nunca X-Forwarded-For direto (o cliente forja). Atrás de proxy, configure
 * server.forward-headers-strategy=native e server.tomcat.remoteip.internal-proxies só com o IP do seu proxy.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> SENSIVEIS = List.of(
            "/auth/login", "/auth/refresh", "/auth/forgot-password", "/auth/change-password");

    private final JanelaFixa porIp;

    public RateLimitFilter(Clock clock, int requisicoesPorMinuto) {
        this.porIp = new JanelaFixa(clock, Duration.ofMinutes(1), requisicoesPorMinuto);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || SENSIVEIS.stream().noneMatch(request.getRequestURI()::endsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        if (!porIp.registrar(ip)) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(porIp.segundosParaLiberar(ip)));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"status\":429,\"detail\":\"Muitas tentativas. Aguarde e tente de novo.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}

/*
 * Bloqueio por conta (cobre ataque distribuído em vários IPs), no serviço de login:
 *
 *   JanelaFixa falhas = new JanelaFixa(clock, Duration.ofMinutes(15), 5);
 *   String chave = email.trim().toLowerCase();
 *   if (falhas.bloqueada(chave)) throw new TooManyRequests(...);    // antes de autenticar
 *   try { autenticar(); falhas.limpar(chave); }
 *   catch (BadCredentialsException e) { falhas.registrar(chave); throw e; }
 */
