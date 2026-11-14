# Exemplos de código

Código de referência para os itens do [guia](../GUIA-SEGURANCA.md). Adapte pacotes, nomes e regras ao seu sistema; não copie sem entender o que cada trecho protege.

| Arquivo | Itens do guia | O que faz |
|---|---|---|
| `spring-boot/AntiEscalada.java` | 6 | Ninguém concede o que não tem nem altera quem tem mais poder |
| `spring-boot/TokenHash.java` | 5, 8 | Token opaco aleatório, hash SHA-256 para guardar e comparação em tempo constante |
| `spring-boot/JanelaFixa.java` | 11 | Contador em janela fixa (Caffeine) para rate limit e bloqueio de login |
| `spring-boot/RateLimitFilter.java` | 11 | 429 + Retry-After por IP nos endpoints de autenticação; bloqueio por conta |
| `spring-boot/ValidadorDeSegredos.java` | 1 | Não sobe em produção com segredo fraco, público ou senha padrão |
| `spring-boot/SecurityHeaders.java` | 5, 15, 16, 18 | API stateless fechada por padrão, CSP, HSTS, Referrer, Permissions-Policy |
| `sql/rls-multitenant.sql` | 3, 4 | Papéis separados, RLS por tenant, função com search_path fixo, conferências |
| `ci/security.yml` | 2, 20 | gitleaks no histórico completo e npm audit no CI |
| `ci/dependabot.yml` | 20 | Atualizações semanais de dependências |
