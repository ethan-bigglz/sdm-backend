package com.example.sdm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "tag_read_log",
    uniqueConstraints = {
        @UniqueConstraint(name = "ukey_uid_ctr", columnNames = {"uid", "read_ctr"})
    }
)
public class TagReadLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String uid;

    @Column(name = "read_ctr", nullable = false)
    private Integer readCtr;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // No-arg constructor required by JPA
    public TagReadLog() {
    }

    public TagReadLog(String uid, Integer readCtr) {
        this.uid = uid;
        this.readCtr = readCtr;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Integer getReadCtr() {
        return readCtr;
    }

    public void setReadCtr(Integer readCtr) {
        this.readCtr = readCtr;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
