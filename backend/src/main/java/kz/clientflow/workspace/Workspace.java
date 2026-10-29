package kz.clientflow.workspace;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Next number for ORD-xxxxxx. Changed only while the row is locked. */
    @Column(nullable = false)
    private int nextOrderNumber = 1;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Embedded
    private Requisites requisites;

    protected Workspace() {
    }

    public Workspace(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Returns the current number and advances the counter. */
    public int takeNextOrderNumber() {
        return nextOrderNumber++;
    }

    public Requisites getRequisites() {
        return requisites == null ? Requisites.empty() : requisites;
    }

    public void setRequisites(Requisites requisites) {
        this.requisites = requisites;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
