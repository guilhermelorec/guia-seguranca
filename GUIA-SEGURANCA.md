# Guia de segurança para aplicações web

Guia reutilizável para qualquer sistema web (API + frontend + banco). Cada item diz **o risco**, **o que fazer**, **como conferir** e traz exemplos para as stacks mais comuns (Spring Boot, Node/Next.js, Supabase/PostgreSQL).

## Regra zero: plano de manutenção antes de qualquer alteração

**Nenhuma correção de segurança começa sem um plano de manutenção escrito.** Antes de mudar código, configuração, banco ou infraestrutura:

1. **Audite** seguindo o [roteiro](#roteiro-de-auditoria) e registre cada achado com severidade, local (arquivo:linha) e cenário de ataque.
2. **Escreva o plano** a partir do [modelo](MODELO-PLANO-MANUTENCAO.md): o que será corrigido agora, o que fica para depois e por quê, o impacto em quem usa o sistema (logout, migration, variável nova obrigatória) e como reverter.
3. **Só então altere**, numa branch própria, com testes para cada correção.
4. **Atualize o plano** ao terminar: status de cada item, como foi validado e as pendências que sobraram.

Por que: mudança de segurança feita às pressas quebra login, derruba sessões, trava deploy (segredo novo obrigatório) ou reabre a falha por outro caminho. O plano obriga a pensar no impacto, deixa a decisão registrada para a próxima auditoria e mostra o que ainda está exposto.

## Como usar este guia

1. Em projeto novo, copie a [checklist](#checklist-rápida) para o README ou para a primeira issue.
2. Em projeto existente, rode a [auditoria](#roteiro-de-auditoria) e **escreva o plano de manutenção antes de corrigir** (regra zero), a partir do [modelo](MODELO-PLANO-MANUTENCAO.md).
3. Repita a auditoria a cada trimestre e sempre que entrar um endpoint público, um upload ou uma integração nova.
4. Código de referência das correções em [exemplos/](exemplos/).

---

## Checklist rápida

| # | Item | Prioridade |
|---|------|-----------|
| 1 | Chaves de API e segredos fora do código e do bundle do frontend | Crítica |
| 2 | Histórico do git sem segredos (e segredos vazados já trocados) | Crítica |
| 3 | Banco: só a chave pública no cliente; chave de serviço só no servidor | Crítica |
| 4 | RLS ligada em toda tabela exposta, com política testada | Crítica |
| 5 | Autenticação e autorização decididas no servidor | Crítica |
| 6 | Acesso restrito por papel e por dono do dado (sem IDOR, sem escalada) | Crítica |
| 7 | Mass assignment bloqueado (DTO com campos permitidos) | Alta |
| 8 | Senhas com hash lento (BCrypt/Argon2) | Crítica |
| 9 | Queries parametrizadas, nunca concatenação | Crítica |
| 10 | Validação de entrada no servidor | Alta |
| 11 | Rate limit em login, cadastro, recuperação de senha e endpoints caros | Alta |
| 12 | Proteção contra robôs nos formulários públicos | Média |
| 13 | Cookies `HttpOnly`, `Secure`, `SameSite` | Alta |
| 14 | Criptografia em trânsito (HTTPS) e em repouso (dados sensíveis) | Alta |
| 15 | HTTPS obrigatório + HSTS | Alta |
| 16 | Security headers (CSP, frame-ancestors, nosniff, referrer, permissions) | Média |
| 17 | Respostas enxutas: DTO de saída, sem campos internos | Alta |
| 18 | Sem vazamento em erros, logs e páginas (stack trace, SQL, PII) | Alta |
| 19 | Uploads restritos (tamanho, tipo, nome, local) | Alta |
| 20 | Scan de dependências e de segredos no CI | Alta |

---

## 1. Esconder as chaves de API

**Risco:** chave no código ou no bundle do navegador é pública. Robôs varrem o GitHub e o JavaScript dos sites atrás delas em minutos.

**Fazer:**
- Segredo só em variável de ambiente ou cofre (Vault, AWS Secrets Manager, Doppler, painel do provedor). No repositório, apenas um `.env.example` com valores vazios.
- No frontend **não existe segredo**. Tudo que começa com `VITE_`, `NEXT_PUBLIC_`, `REACT_APP_` vai para o bundle. Chamada que precisa de chave passa por um endpoint do seu backend.
- Sem valor padrão para segredo no código (`${JWT_SECRET:valor}` é segredo público). A aplicação deve **recusar subir** em produção sem o segredo ou com um valor conhecido.
- Uma chave por ambiente (dev ≠ homologação ≠ produção) e com o menor escopo possível.

**Conferir:**
```bash
git grep -nE "(api[_-]?key|secret|token|password|senha)\s*[:=]\s*['\"][^'\"]{8,}" -- ':!*.example' ':!*test*'
grep -rE "sk_live|AKIA|xkeysib-|\$aact_|eyJhbGci" dist/ build/ .next/ 2>/dev/null   # bundle gerado
```

## 2. Limpar segredos do git

**Risco:** apagar o arquivo num commit novo não adianta; o segredo continua no histórico e em todo clone e fork.

**Fazer:**
1. **Primeiro troque o segredo** no provedor. Considere vazado tudo que já foi commitado, mesmo em repositório privado.
2. Depois limpe o histórico com `git filter-repo` (ou BFG):
   ```bash
   pip install git-filter-repo
   git filter-repo --path .env --invert-paths              # remove um arquivo de todo o histórico
   git filter-repo --replace-text segredos.txt             # troca strings: uma por linha, "valor==>REMOVIDO"
   git push --force --all && git push --force --tags      # combinar com a equipe: todos reclonam
   ```
3. Peça ao GitHub para limpar caches de PR, se for o caso, e ligue o **Secret scanning + Push protection** (Settings > Code security).
4. Previna: `.gitignore` com `.env*`, `*.pem`, `*.key`, `*.pfx`, `*.p12`, `*.jks`; hook de pre-commit com gitleaks; gitleaks no CI.

**Conferir:**
```bash
gitleaks git . --redact          # histórico inteiro
gitleaks dir . --redact          # árvore atual, inclusive arquivos não versionados
```

## 3. Chave pública do banco no cliente (Supabase/Firebase)

**Risco:** a `service_role` (Supabase) ou credencial admin ignora RLS. No cliente, qualquer um lê e apaga o banco inteiro.

**Fazer:**
- No navegador/app, só a chave **anon/publishable**. A `service_role`, a senha do Postgres e a connection string ficam só no servidor.
- A chave anon é pública por desenho: a segurança vem da RLS (item 4), nunca de esconder a chave.
- Se o backend fala direto com o Postgres (sem PostgREST), revogue tudo de `anon` e `authenticated` no schema `public`:
  ```sql
  revoke all on all tables in schema public from anon, authenticated;
  alter default privileges in schema public revoke all on tables from anon, authenticated;
  ```
- Separe papéis: um **dono das tabelas** (migrations) e um **usuário da aplicação** só com `SELECT/INSERT/UPDATE/DELETE`, sem `BYPASSRLS`.

**Conferir:** `grep -r service_role frontend/ src/ dist/` não pode achar nada; consulte grants:
```sql
select grantee, count(*) from information_schema.role_table_grants
 where table_schema = 'public' group by grantee;
```

## 4. Ativar RLS

**Risco:** sem Row Level Security, uma falha no código (ou a chave anon) expõe dados de todos os clientes.

**Fazer:**
- `alter table x enable row level security;` em **toda** tabela do schema exposto. Use `force row level security` se o usuário da aplicação for dono da tabela.
- Política por dono/tenant, com `USING` (leitura) e `WITH CHECK` (escrita):
  ```sql
  create policy isolamento_tenant on pedido
    using (tenant_id = (select app_tenant_atual()))
    with check (tenant_id = (select app_tenant_atual()));
  ```
  - Envolva funções em `(select ...)`: o Postgres avalia uma vez por consulta, não por linha.
  - Funções usadas em política com `set search_path = pg_catalog, pg_temp` e nomes qualificados.
  - Supabase: use `auth.uid()` dentro de `(select auth.uid())`.
- Tabela global (planos, catálogo): RLS ligada com política só de leitura, ou sem grant nenhum para papéis expostos.
- **Teste de isolamento** automatizado: usuário da conta A não lê, não altera e não apaga nada da conta B.

**Conferir:**
```sql
select relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity;   -- deve voltar vazio ou só tabelas sem grant
```
No Supabase: painel **Advisors > Security** (ou `get_advisors`) depois de cada migration.

## 5. Autenticação no servidor

**Risco:** checagem só no frontend (esconder botão, rota protegida no React) é contornada com `curl`.

**Fazer:**
- Toda rota da API exige autenticação por padrão (`anyRequest().authenticated()`); endpoints públicos são uma lista explícita e curta.
- Token validado em toda requisição: assinatura, expiração, emissor; usuário e conta ainda ativos (consulte o banco ou use token curto).
- Access token curto (5–15 min) e refresh token longo, **rotativo**, guardado como **hash** no banco e revogado no logout e na troca de senha.
- Segredo de assinatura com no mínimo 256 bits, ou chave assimétrica (Ed25519/RS256) quando outros serviços validam.
- Trocar senha revoga todas as sessões.

## 6. Restringir acessos

**Risco:** IDOR (trocar o `id` na URL e ver dado alheio) e escalada de privilégio (usuário comum se dando papel de admin).

**Fazer:**
- Autorização em cada endpoint (`@PreAuthorize`, middleware), por permissão e não só por "está logado".
- Toda busca por id filtra pelo dono/tenant (no código **e** na RLS).
- **Anti-escalada:** quem gerencia usuários só concede papéis/permissões que ele mesmo tem, e não edita, desativa ou troca a senha de quem tem mais poder.
- Papel de plataforma/superadmin nunca atribuível pela API do cliente.
- Endpoints internos (métricas, actuator, admin) fora da internet: rede interna, loopback ou autenticação própria.

**Conferir:** para cada papel, tente cada endpoint de escrita com um token desse papel; tente acessar id de outro tenant; tente `PUT /users/{meu-id}` com `roles: ["ADMIN"]`.

## 7. Bloquear mass assignment

**Risco:** a API recebe a entidade inteira e o atacante manda `"admin": true`, `"contaId": 2`, `"saldo": 99999`.

**Fazer:**
- Nunca receba entidade de banco no `@RequestBody`/`req.body`. Use DTO de entrada só com os campos editáveis.
- Campos sensíveis (dono, tenant, papel, status, preço calculado) vêm do servidor, nunca do cliente.
- Quando o DTO tem campo sensível (ex.: `roles`), aplique a regra de autorização do item 6 nele.
- Node: valide com Zod/Joi em modo estrito (`.strict()`) e copie campos explicitamente; Mongoose: `strict: true`.

## 8. Hash nas senhas

**Fazer:**
- BCrypt (custo ≥ 10), Argon2id ou scrypt. Nunca MD5/SHA-1/SHA-256 puro para senha.
- SHA-256 serve para **tokens aleatórios de alta entropia** (refresh token, link de confirmação), não para senha.
- Política: mínimo 8–12 caracteres, bloquear senhas comuns, sem regra de "trocar a cada 90 dias".
- Senha inicial de admin vem do ambiente; nunca `Admin@123` como padrão. Forçar troca no primeiro acesso.
- Comparação de segredos em tempo constante (`MessageDigest.isEqual`, `crypto.timingSafeEqual`).

## 9. Queries parametrizadas

**Fazer:**
- JPA/JPQL com `:parametro`, Spring Data, `PreparedStatement`, ORMs (Prisma, Drizzle) ou `$1` no `pg`.
- Montagem dinâmica só de **trechos fixos** do código; valores sempre por parâmetro. Nome de coluna/ordenação vindo do cliente passa por lista branca.
- Nunca `"... WHERE nome = '" + nome + "'"`, nem `format('%s')` com dado do usuário em SQL dinâmico (use `%L`/`%I`).

**Conferir:**
```bash
grep -rnE '(createQuery|createNativeQuery|query|execute)\(\s*"[^"]*"\s*\+' src/
```

## 10. Validação de inputs

**Fazer:**
- Validação no servidor (Bean Validation `@Valid`, Zod), mesmo que o frontend também valide.
- Tamanho máximo em todo texto, faixa em números, formato em e-mail/CPF/CNPJ/datas, enums fechados.
- Limite de tamanho do corpo da requisição e de itens em listas.
- Saída escapada no frontend (React já escapa; evite `dangerouslySetInnerHTML`/`innerHTML` com dado do usuário; se precisar, DOMPurify).
- Redirecionamentos só para caminhos internos (evita open redirect).

## 11. Rate limit

**Fazer:**
- Login: limite por IP **e** por conta (ex.: 5 falhas em 15 min bloqueiam o e-mail temporariamente).
- Cadastro, recuperação de senha, envio de e-mail/SMS, ativação de dispositivos, endpoints caros (relatórios, exportação).
- Responder `429` com `Retry-After`.
- Use o IP da conexão. `X-Forwarded-For` só é confiável quando vem do **seu** proxy (configure a lista de proxies confiáveis).
- Em várias instâncias, contador compartilhado (Redis, Bucket4j, Upstash) ou limite no gateway/WAF (Cloudflare, Vercel Firewall, Nginx `limit_req`).

## 12. Proteção contra robôs

**Fazer:**
- CAPTCHA invisível (Cloudflare Turnstile, hCaptcha, reCAPTCHA v3) nos formulários públicos: cadastro, contato, login depois de N falhas.
- Validação do token do CAPTCHA **no servidor**.
- Honeypot (campo escondido que humano não preenche) e tempo mínimo de preenchimento como camada barata.
- WAF/bot management do provedor de borda para tráfego volumoso.

## 13. Proteger cookies

**Fazer:**
- Cookie de sessão/refresh: `HttpOnly; Secure; SameSite=Strict` (ou `Lax`), `Path` restrito (ex.: `/api/auth`), prefixo `__Host-`.
- Token em `localStorage` é lido por qualquer XSS. Prefira refresh token em cookie `HttpOnly` e access token só em memória.
- Com cookie de autenticação, ative proteção CSRF (token ou `SameSite=Strict` + checagem de `Origin`).
- CORS: lista exata de origens; nunca `*` com `allowCredentials`.

## 14. Criptografia de dados

**Fazer:**
- **Em trânsito:** TLS em tudo, inclusive app → banco (`sslmode=require`/`verify-full`) e chamadas a terceiros.
- **Em repouso:** disco do banco cifrado (padrão nos provedores gerenciados) **e** cifra de campo para o que é muito sensível: certificados, chaves de terceiros, dados bancários, documentos.
  - AES-256-GCM com IV aleatório por registro; chave fora do banco (variável/KMS); plano de rotação de chave.
  - Hash (não cifra) para o que só precisa ser comparado.
- Backups também cifrados e com acesso restrito.

## 15. Forçar HTTPS

**Fazer:**
- Redirecionar HTTP → HTTPS no proxy/borda (Nginx, Cloudflare, Vercel, load balancer).
- Header `Strict-Transport-Security: max-age=31536000; includeSubDomains` (depois de estável, `preload`).
- App atrás de proxy: configure os forwarded headers para a app saber que a origem era HTTPS, restringindo quais proxies são confiáveis.

## 16. Security headers

| Header | Valor sugerido (API) | SPA |
|---|---|---|
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | `default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'self'` |
| `X-Content-Type-Options` | `nosniff` | igual |
| `X-Frame-Options` | `DENY` | `SAMEORIGIN` |
| `Referrer-Policy` | `no-referrer` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` | conforme uso |
| `Strict-Transport-Security` | ver item 15 | igual |
| `Cross-Origin-Opener-Policy` | `same-origin` | igual |

Spring Security já envia `nosniff`, `X-Frame-Options` e `Cache-Control: no-store`; acrescente CSP, Referrer e Permissions. Next.js: `headers()` no `next.config.js`. Vercel/Netlify: arquivo de configuração. Confira em <https://securityheaders.com>.

## 17. Trim nas respostas da API

**Risco:** devolver a entidade inteira expõe hash de senha, campos internos, ids de outros sistemas, dados de outros usuários.

**Fazer:**
- DTO de saída explícito por endpoint; nunca serializar entidade JPA/ORM direto.
- Paginação com tamanho máximo (`size` ≤ 100).
- Não devolver segredo nunca: nem hash de senha, nem token de terceiro, nem arquivo de certificado.
- Erros com mensagem genérica no login ("e-mail ou senha inválidos") para não revelar quais e-mails existem.

## 18. Não vazar conteúdo

**Fazer:**
- Handler global de erro: mensagem genérica para erro 500 e detalhe só no log. Spring: `server.error.include-stacktrace=never`, `include-message=never`.
- Swagger/OpenAPI, actuator, consoles de banco e source maps desligados ou protegidos em produção.
- Logs sem senha, token, número de cartão ou documento completo. Mascarar PII.
- Health check público sem detalhes (`show-details=never`).
- `robots.txt`/`noindex` em painéis administrativos; diretórios sem listagem.

## 19. Restringir uploads

**Fazer:**
- Limite global de tamanho (Spring `spring.servlet.multipart.max-file-size`, Nginx `client_max_body_size`) e limite por endpoint.
- Lista branca de extensão **e** checagem do conteúdo (magic bytes ou abrir o arquivo com a biblioteca certa).
- Renomear para um id aleatório; nunca usar o nome enviado como caminho (path traversal).
- Guardar fora da pasta servida pela web, ou em storage com URL assinada e expiração.
- Servir com `Content-Disposition: attachment` e o `Content-Type` correto; imagens reprocessadas.
- Antivírus (ClamAV) quando outros usuários baixam o arquivo.

## 20. Scan de dependências

**Fazer:**
- Dependabot/Renovate ligado (pacotes, GitHub Actions, imagens Docker).
- CI quebra com vulnerabilidade alta/crítica em dependência de produção: `npm audit --omit=dev --audit-level=high`, `pip-audit`, OWASP Dependency-Check ou Trivy para Maven/Gradle.
- Gitleaks no CI e no pre-commit.
- Lockfile versionado; `npm ci` no CI. Desconfie de pacote novo com nome parecido com um famoso (typosquatting) e de `postinstall`.
- GitHub Actions de terceiros fixadas por SHA do commit.
- Imagem Docker mínima, usuário não root, sem ferramentas desnecessárias.

---

## Roteiro de auditoria

Ordem sugerida, do que mais causa estrago para o menos:

1. **Segredos:** gitleaks no histórico e na árvore; `.gitignore`; bundle do frontend; defaults no código.
2. **Banco:** grants por papel; RLS em todas as tabelas; advisors do provedor; teste de isolamento entre tenants.
3. **Autenticação:** lista de endpoints públicos; validação do token; refresh rotativo com hash; revogação.
4. **Autorização:** cada endpoint de escrita × cada papel; IDOR; escalada pelo cadastro de usuários.
5. **Entradas:** DTOs, validação, SQL dinâmico, uploads, redirecionamentos.
6. **Saídas:** DTOs de resposta, handler de erro, logs, Swagger/actuator em produção.
7. **Borda:** HTTPS/HSTS, headers, CORS, cookies, rate limit, CAPTCHA.
8. **Cadeia de suprimentos:** `npm audit`/Dependabot/Trivy, Actions por SHA, imagem Docker.

Para cada achado, registre no plano de manutenção (regra zero): **severidade** (crítica/alta/média/baixa), **onde** (arquivo:linha), **cenário de ataque**, **correção**, **status**. Só comece a corrigir com o plano escrito.

## Rotina de manutenção

| Frequência | Tarefa |
|---|---|
| A cada PR | CI com testes, gitleaks e audit de dependências; revisão de qualquer endpoint público, upload ou SQL dinâmico |
| Semanal | Revisar PRs do Dependabot |
| Mensal | Advisors do banco; logs de 401/403/429 em busca de padrão de ataque; usuários admin ativos |
| Trimestral | Auditoria completa (roteiro acima) e plano de manutenção atualizado; teste de restauração de backup |
| Semestral | Rotação de segredos (JWT, chaves de API, senhas de banco); revisão de papéis e permissões |
| Em incidente | Trocar segredos primeiro, investigar depois; revogar sessões; registrar postmortem |
