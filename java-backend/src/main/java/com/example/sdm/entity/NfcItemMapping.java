package com.example.sdm.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "nfc_item_mapping")
public class NfcItemMapping {

    @Id
    @Column(nullable = false, length = 50)
    private String uid;

    @Column(name = "item_cd", nullable = false, length = 50)
    private String itemCd;

    // No-arg constructor required by JPA
    public NfcItemMapping() {
    }

    public NfcItemMapping(String uid, String itemCd) {
        this.uid = uid;
        this.itemCd = itemCd;
    }

    // Getters and Setters
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getItemCd() {
        return itemCd;
    }

    public void setItemCd(String itemCd) {
        this.itemCd = itemCd;
    }
}
