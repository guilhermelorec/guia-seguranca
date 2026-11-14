package com.exemplo.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (refresh, confirmação de e-mail, reset de senha). Guia, itens 5 e 8.
 * O cliente recebe o valor puro; o banco guarda só o hash. Quem ler a tabela não consegue usar o token.
 * NÃO use para senha: senha tem pouca entropia e precisa de hash lento (BCrypt/Argon2).
 */
public final class TokenHash {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHash() {
    }

    /** 256 bits aleatórios em base64url. */
    public static String novoToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparação em tempo constante. */
    public static boolean iguais(String a, String b) {
        return a != null && b != null
                && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
