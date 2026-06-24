package com.example.sdm.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "nfc_uid", length = 14, unique = true, nullable = true)
    private String nfcUid;


    @Column(name = "last_ctr", nullable = false)
    private Integer lastCtr = 0;

    @Column(name = "nft_token_id", unique = true, nullable = false)
    private Integer nftTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "unclaimed";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.lastCtr == null) {
            this.lastCtr = 0;
        }
        if (this.status == null) {
            this.status = "unclaimed";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // No-arg constructor
    public Item() {
    }

    // Constructor
    public Item(Product product, String nfcUid, Integer nftTokenId, User owner, String status) {
        this.product = product;
        this.nfcUid = nfcUid;
        this.nftTokenId = nftTokenId;
        this.owner = owner;
        this.status = status;
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public String getNfcUid() {
        return nfcUid;
    }

    public void setNfcUid(String nfcUid) {
        this.nfcUid = nfcUid;
    }

    public Integer getLastCtr() {
        return lastCtr;
    }

    public void setLastCtr(Integer lastCtr) {
        this.lastCtr = lastCtr;
    }

    public Integer getNftTokenId() {
        return nftTokenId;
    }

    public void setNftTokenId(Integer nftTokenId) {
        this.nftTokenId = nftTokenId;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
