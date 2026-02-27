package com.gustavocirino.myday_productivity.service;

import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Migração temporária para ambiente de desenvolvimento.
 *
 * Ao iniciar a aplicação, atribui todas as tarefas sem usuário (user_id NULL)
 * ao primeiro usuário cadastrado no sistema. Isso evita que o dashboard
 * apareça vazio após a introdução do isolamento por usuário.
 *
 * Regra de segurança: se algum profile "prod" / "production" estiver ativo,
 * a migração é automaticamente ignorada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DevOrphanTaskMigration {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Environment environment;

    @PostConstruct
    @Transactional
    public void migrateOrphanTasksToFirstUser() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProd = Arrays.stream(activeProfiles)
                .filter(p -> p != null)
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (isProd) {
            log.info("DevOrphanTaskMigration: ambiente de produção detectado, migração ignorada.");
            return;
        }

        List<Task> orphans = taskRepository.findByUserIsNull();
        if (orphans.isEmpty()) {
            return;
        }

        Optional<User> firstUserOpt = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .findFirst();

        if (firstUserOpt.isEmpty()) {
            log.warn("DevOrphanTaskMigration: existem tarefas órfãs, mas nenhum usuário cadastrado.");
            return;
        }

        User owner = firstUserOpt.get();
        orphans.forEach(task -> task.setUser(owner));
        taskRepository.saveAll(orphans);

        log.info("DevOrphanTaskMigration: atribuídas {} tarefas órfãs ao usuário {} (id={})",
                orphans.size(), owner.getEmail(), owner.getId());
    }
}
