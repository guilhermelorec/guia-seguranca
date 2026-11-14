package com.exemplo.seguranca;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Recusa subir em produção com segredo ausente, curto ou já público (guia, item 1).
 * Mantenha em SEGREDOS_CONHECIDOS todo valor que já esteve num arquivo versionado.
 */
@Component
public class ValidadorDeSegredos implements InitializingBean {

    private static final int JWT_MINIMO_BYTES = 32;
    private static final Set<String> SEGREDOS_CONHECIDOS = Set.of("change-me", "secret", "dev-secret");
    private static final Set<String> SENHAS_CONHECIDAS = Set.of("admin", "admin@123", "123456", "password");

    private final Environment env;
    private final String jwtSecret;
    private final String adminPassword;

    public ValidadorDeSegredos(Environment env,
                               @Value("${app.jwt.secret:}") String jwtSecret,
                               @Value("${app.admin.password:}") String adminPassword) {
        this.env = env;
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
    }

    @Override
    public void afterPropertiesSet() {
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < JWT_MINIMO_BYTES
                || SEGREDOS_CONHECIDOS.contains(jwtSecret)) {
            throw new IllegalStateException("JWT secret ausente, curto ou público. Gere com: openssl rand -base64 48");
        }
        if (SENHAS_CONHECIDAS.contains(adminPassword.toLowerCase())) {
            throw new IllegalStateException("Senha inicial do admin é uma senha padrão conhecida");
        }
    }
}
