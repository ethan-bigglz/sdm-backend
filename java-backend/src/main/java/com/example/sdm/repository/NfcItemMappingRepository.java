package com.example.sdm.repository;

import com.example.sdm.entity.NfcItemMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NfcItemMappingRepository extends JpaRepository<NfcItemMapping, String> {
    Optional<NfcItemMapping> findByUid(String uid);
}
