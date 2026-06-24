package com.example.sdm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "brands")
public class Brand {

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Column(name = "theme_color", length = 7, nullable = false)
    private String themeColor;

    @Column(name = "name_en", length = 100, nullable = false)
    private String nameEn;

    @Column(name = "name_ko", length = 100, nullable = false)
    private String nameKo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_image_url", length = 255)
    private String logoImageUrl;

    @Column(name = "cover_image_url", length = 255)
    private String coverImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor
    public Brand() {
    }

    // Full constructor
    public Brand(String id, String themeColor, String nameEn, String nameKo, String description, String logoImageUrl, String coverImageUrl) {
        this.id = id;
        this.themeColor = themeColor;
        this.nameEn = nameEn;
        this.nameKo = nameKo;
        this.description = description;
        this.logoImageUrl = logoImageUrl;
        this.coverImageUrl = coverImageUrl;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getThemeColor() {
        return themeColor;
    }

    public void setThemeColor(String themeColor) {
        this.themeColor = themeColor;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getNameKo() {
        return nameKo;
    }

    public void setNameKo(String nameKo) {
        this.nameKo = nameKo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLogoImageUrl() {
        return logoImageUrl;
    }

    public void setLogoImageUrl(String logoImageUrl) {
        this.logoImageUrl = logoImageUrl;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
