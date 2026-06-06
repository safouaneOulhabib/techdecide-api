package com.techdecide.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "decisions")
public class Decision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String consequences;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alternative> alternatives;

    @OneToMany(mappedBy = "decision", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments;

    @ManyToMany
    @JoinTable(
            name = "decision_tags",
            joinColumns = @JoinColumn(name = "decision_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private List<Tag> tags;

    @Column(name = "review_date")
    private LocalDateTime reviewDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superseded_by_id")
    private Decision supersededBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = Status.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        DRAFT,
        PROPOSED,
        APPROVED,
        REJECTED,
        SUPERSEDED;

        private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            DRAFT,      Set.of(PROPOSED),
            PROPOSED,   Set.of(APPROVED, REJECTED, DRAFT),
            APPROVED,   Set.of(SUPERSEDED),
            REJECTED,   Set.of(DRAFT),
            SUPERSEDED, Set.of()
        );

        public boolean canTransitionTo(Status next) {
            return ALLOWED.getOrDefault(this, Set.of()).contains(next);
        }
    }
}