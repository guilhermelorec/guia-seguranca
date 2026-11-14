# Plano de manutenção de segurança: <nome do sistema>

> Regra zero do [guia](GUIA-SEGURANCA.md): este plano é escrito **antes** de qualquer alteração e atualizado ao final. Guarde o plano preenchido no repositório do próprio sistema (ex.: `docs/seguranca/PLANO-MANUTENCAO.md`), nunca num lugar público: ele descreve falhas ainda abertas.

- **Data da auditoria:** AAAA-MM-DD
- **Branch das correções:** `fix/seguranca-...`
- **Escopo:** backend, frontend, banco, infraestrutura, CI, histórico do git (marque o que entrou)
- **Responsável:**

## 1. Resumo

| Situação | Itens |
|---|---|
| Corrigir nesta entrega | |
| Já está correto (verificado) | |
| Pendente (fica para depois) | |

Achado mais grave, em uma frase:

## 2. Achados

| # | Severidade | Tema do guia | Onde (arquivo:linha) | Cenário de ataque | Correção proposta |
|---|---|---|---|---|---|
| A1 | Crítica / Alta / Média / Baixa | | | | |

## 3. O que será corrigido agora

| # | Achado | Mudança | Impacto para quem usa | Como reverter | Teste que prova |
|---|---|---|---|---|---|
| C1 | A1 | | ex.: usuários deslogados, migration, variável nova obrigatória | | |

### Antes de começar
- [ ] Segredos já vazados foram **trocados** (não basta apagar do código)
- [ ] Migrations revisadas: idempotentes? apagam dados? quanto tempo travam a tabela?
- [ ] Variáveis de ambiente novas documentadas no `.env.example` e no deploy
- [ ] Plano de rollback definido

## 4. Já está correto (verificado)

| Tema | Evidência (comando, consulta, arquivo) |
|---|---|
| | |

## 5. Pendências

| # | Prioridade | Tema | Pendência | Por que importa | Como fazer | Esforço |
|---|---|---|---|---|---|---|
| P1 | Alta / Média / Baixa | | | | | |

## 6. Resultado (preencher ao terminar)

- **Status de cada item:** feito / adiado / descartado (com motivo)
- **Validação:** testes, build, varreduras (gitleaks, audit, advisors) e seus resultados
- **PR / commits:**
- **Próxima auditoria:** AAAA-MM-DD
