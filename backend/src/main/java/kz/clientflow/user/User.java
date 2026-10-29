package kz.clientflow.user;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String fullName;

    /** Always stored in lower case. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean emailVerified;

    /** "kk", "ru" or "en". */
    @Column(nullable = false, length = 2)
    private String preferredLanguage = "ru";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Theme theme = Theme.SYSTEM;

    @Column(nullable = false)
    private boolean notifyDeadlines = true;

    @Column(nullable = false)
    private boolean notifyOverdue = true;

    @Column(nullable = false)
    private boolean notifyOrderChanges = true;

    @Column(nullable = false)
    private boolean notifyPayments = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, String preferredLanguage) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.preferredLanguage = preferredLanguage;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public boolean isNotifyDeadlines() {
        return notifyDeadlines;
    }

    public void setNotifyDeadlines(boolean notifyDeadlines) {
        this.notifyDeadlines = notifyDeadlines;
    }

    public boolean isNotifyOverdue() {
        return notifyOverdue;
    }

    public void setNotifyOverdue(boolean notifyOverdue) {
        this.notifyOverdue = notifyOverdue;
    }

    public boolean isNotifyOrderChanges() {
        return notifyOrderChanges;
    }

    public void setNotifyOrderChanges(boolean notifyOrderChanges) {
        this.notifyOrderChanges = notifyOrderChanges;
    }

    public boolean isNotifyPayments() {
        return notifyPayments;
    }

    public void setNotifyPayments(boolean notifyPayments) {
        this.notifyPayments = notifyPayments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
