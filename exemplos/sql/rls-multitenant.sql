-- RLS multi-tenant no PostgreSQL/Supabase (guia, itens 3 e 4).
-- A aplicação define o tenant em cada conexão/transação: SELECT set_config('app.tenant_id', '42', true);

-- 1) Papéis: dono das tabelas (migrations) separado do usuário da aplicação.
--    O usuário da aplicação nunca é dono das tabelas nem tem BYPASSRLS.
-- CREATE ROLE app_migrator LOGIN PASSWORD '...';
-- CREATE ROLE app_user     LOGIN PASSWORD '...';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_user;
-- ALTER DEFAULT PRIVILEGES FOR ROLE app_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user;

-- Supabase com backend próprio (sem PostgREST): papéis expostos sem acesso nenhum.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;

-- 2) Tenant atual. search_path fixo e nomes qualificados: ninguém troca a função por outra.
CREATE OR REPLACE FUNCTION app_tenant_atual() RETURNS BIGINT
    LANGUAGE sql STABLE PARALLEL SAFE
    SET search_path = pg_catalog, pg_temp
AS $$
    SELECT COALESCE(NULLIF(pg_catalog.current_setting('app.tenant_id', true), ''), '-1')::BIGINT
$$;

-- 3) Política por tabela. (SELECT fn()) é avaliado uma vez por consulta, não uma vez por linha.
ALTER TABLE pedido ENABLE ROW LEVEL SECURITY;
-- ALTER TABLE pedido FORCE ROW LEVEL SECURITY;   -- se o usuário da aplicação for dono da tabela
CREATE POLICY isolamento_tenant ON pedido
    USING (tenant_id = (SELECT app_tenant_atual()))
    WITH CHECK (tenant_id = (SELECT app_tenant_atual()));

-- 4) Tokens opacos guardados só como hash. Migração de tokens antigos em texto puro (rodar uma única vez):
-- UPDATE refresh_token SET token = encode(sha256(convert_to(token, 'UTF8')), 'hex');
-- DELETE FROM refresh_token WHERE revoked OR expires_at < now();

-- 5) Conferências
-- Tabelas sem RLS:
SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE n.nspname = 'public' AND c.relkind = 'r' AND NOT c.relrowsecurity ORDER BY 1;
-- Ninguém da aplicação ignora RLS:
SELECT rolname, rolbypassrls, rolsuper FROM pg_roles WHERE rolname NOT LIKE 'pg_%' ORDER BY 1;
-- Grants por papel no schema public:
SELECT grantee, count(*) FROM information_schema.role_table_grants WHERE table_schema = 'public' GROUP BY 1;
