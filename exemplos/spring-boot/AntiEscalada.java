package com.exemplo.seguranca;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Anti-escalada de privilégio (guia, item 6). Use no serviço que cria e edita usuários:
 *
 *   antiEscalada.exigirAlvoGerenciavel(permissoesEfetivas(alvo));        // antes de editar/desativar/excluir
 *   antiEscalada.exigirQuePossuiTudo(permissoesEfetivas(novosPapeis));    // antes de conceder papéis/permissões
 *
 * Regra: ninguém concede o que não tem e ninguém altera quem tem mais poder que ele.
 * "Permissões efetivas" = permissões de todos os papéis + permissões diretas. Inclua um marcador para o papel de
 * administrador (ex.: "ROLE_ADMIN") se ele der poderes que não aparecem como permissão.
 */
@Component
public class AntiEscalada {

    /** Autoridades de quem faz a requisição. Vazio quando a chamada vem do próprio sistema (seed, job). */
    public Optional<Set<String>> autoridadesDoSolicitante() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return Optional.of(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet()));
    }

    public void exigirQuePossuiTudo(Set<String> concedidas) {
        autoridadesDoSolicitante().ifPresent(minhas -> {
            if (!minhas.containsAll(concedidas)) {
                throw new AccessDeniedException("Você não pode conceder papéis ou permissões que não possui");
            }
        });
    }

    public void exigirAlvoGerenciavel(Set<String> permissoesDoAlvo) {
        autoridadesDoSolicitante().ifPresent(minhas -> {
            if (!minhas.containsAll(permissoesDoAlvo)) {
                throw new AccessDeniedException("Você não pode alterar um usuário com mais permissões que as suas");
            }
        });
    }
}
