# Guia de segurança para aplicações web

Instruções de segurança para sistemas web (API + frontend + banco): o que verificar, como corrigir e como manter. Aqui ficam só documentação e código de referência genérico.

## Regra zero

**Antes de qualquer alteração de segurança, escreva um plano de manutenção.** Audite, registre os achados, decida o que entra agora e o que fica para depois, preveja o impacto e só então altere. Detalhes em [GUIA-SEGURANCA.md](GUIA-SEGURANCA.md#regra-zero-plano-de-manutenção-antes-de-qualquer-alteração).

## Conteúdo

| Caminho | Para quê |
|---|---|
| [GUIA-SEGURANCA.md](GUIA-SEGURANCA.md) | Os 20 temas (segredos, RLS, auth, rate limit, headers, uploads, dependências...), roteiro de auditoria e rotina |
| [MODELO-PLANO-MANUTENCAO.md](MODELO-PLANO-MANUTENCAO.md) | Modelo do plano a preencher antes de mexer em qualquer sistema |
| [exemplos/](exemplos/) | Código de referência (Spring Boot, SQL/PostgreSQL, GitHub Actions) |

## Fluxo

1. Copie `MODELO-PLANO-MANUTENCAO.md` para o repositório do sistema que vai auditar.
2. Siga o roteiro de auditoria do guia e preencha os achados.
3. Defina o que corrigir agora e o impacto de cada mudança.
4. Corrija numa branch própria, com testes para cada correção.
5. Atualize o plano com o resultado e as pendências.

> O plano preenchido descreve falhas ainda abertas: mantenha-o no repositório privado do sistema, nunca aqui.
