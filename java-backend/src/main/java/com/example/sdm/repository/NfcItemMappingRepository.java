package com.example.sdm.repository;

import com.example.sdm.entity.NfcItemMapping;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NfcItemMappingRepository extends JpaRepository<NfcItemMapping, String> {
    Optional<NfcItemMapping> findByUid(String uid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from NfcItemMapping m where m.uid = :uid")
    Optional<NfcItemMapping> findByUidForUpdate(@Param("uid") String uid);
}

