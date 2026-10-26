package com.embedding.pgvector.config;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import com.embedding.pgvector.workspace.repository.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final WorkspaceRepository workspaceRepository;
    private final Clock clock;

    public DevDataSeeder(WorkspaceRepository workspaceRepository, Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (workspaceRepository.count() > 0) {
            log.info("Dev data already present, skipping seed");
            return;
        }

        Instant now = Instant.now(clock);
        Workspace demoWorkspace = Workspace.create("Dalga Fiber", now);
        demoWorkspace.addSource("Yardım merkezi", SourceKind.WEB_CRAWL);
        demoWorkspace.addSource("Fatura SSS", SourceKind.MARKDOWN);
        workspaceRepository.save(demoWorkspace);

        log.info("Dev data seeded workspaces={} sources={}", 1, 2);
    }
}
