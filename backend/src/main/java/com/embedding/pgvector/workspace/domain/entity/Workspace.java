package com.embedding.pgvector.workspace.domain.entity;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Persistable;

/**
 * Bir çalışma alanını ve kaynaklarını temsil eder; kaynak eklerken kaynak sınırını ve ad tekilliğini uygular.
 */
@Entity
@Table(name = "workspaces")
public class Workspace implements Persistable<Long> {

    public static final int MAX_SOURCES_PER_WORKSPACE = 10;

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "workspace", fetch = FetchType.LAZY)
    private List<Source> sources = new ArrayList<>();

    @Transient
    private boolean newEntity = true;

    protected Workspace() {
    }

    private Workspace(long id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public static Workspace seeded(long id, String name, Instant createdAt) {
        return new Workspace(id, name, createdAt);
    }

    public Source addSource(long sourceId, String sourceName, SourceKind kind, Instant createdAt) {
        if (sources.size() >= MAX_SOURCES_PER_WORKSPACE) {
            throw new ConflictException("SOURCE_LIMIT_REACHED", "Workspace source limit reached: " + id);
        }
        String nameKey = Source.toNameKey(sourceName);
        boolean nameTaken = sources.stream()
                .anyMatch(source -> source.getNameKey().equals(nameKey));
        if (nameTaken) {
            throw new ConflictException("SOURCE_NAME_TAKEN", "Source name is already used in workspace: " + id);
        }
        Source source = Source.ingesting(this, sourceId, sourceName, kind, createdAt);
        sources.add(source);
        return source;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Source> getSources() {
        return Collections.unmodifiableList(sources);
    }
}
