package com.exemplo.seguranca;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Contador por chave em janela fixa, em memória (guia, item 11). Base do rate limit por IP e do bloqueio de login.
 * Com várias instâncias cada uma conta sozinha: troque por Redis/Bucket4j mantendo a mesma interface.
 */
public final class JanelaFixa {

    private static final class Janela {
        Instant inicio;
        int total;

        Janela(Instant inicio) {
            this.inicio = inicio;
        }
    }

    private final Clock clock;
    private final Duration duracao;
    private final int limite;
    private final Cache<String, Janela> cache;

    public JanelaFixa(Clock clock, Duration duracao, int limite) {
        this.clock = clock;
        this.duracao = duracao;
        this.limite = limite;
        this.cache = Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(duracao)
                .ticker(() -> TimeUnit.MILLISECONDS.toNanos(clock.millis()))
                .build();
    }

    /** Conta uma ocorrência; false quando a chave já passou do limite na janela. */
    public synchronized boolean registrar(String chave) {
        Instant agora = clock.instant();
        Janela j = cache.get(chave, k -> new Janela(agora));
        if (!agora.isBefore(j.inicio.plus(duracao))) {
            j.inicio = agora;
            j.total = 0;
        }
        if (j.total >= limite) {
            return false;
        }
        j.total++;
        return true;
    }

    public synchronized boolean bloqueada(String chave) {
        Janela j = cache.getIfPresent(chave);
        return j != null && clock.instant().isBefore(j.inicio.plus(duracao)) && j.total >= limite;
    }

    public synchronized void limpar(String chave) {
        cache.invalidate(chave);
    }

    public synchronized long segundosParaLiberar(String chave) {
        Janela j = cache.getIfPresent(chave);
        return j == null ? 0 : Math.max(1, Duration.between(clock.instant(), j.inicio.plus(duracao)).toSeconds());
    }
}
