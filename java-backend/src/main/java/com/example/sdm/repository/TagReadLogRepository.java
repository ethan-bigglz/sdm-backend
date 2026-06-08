package com.example.sdm.repository;

import com.example.sdm.entity.TagReadLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TagReadLogRepository extends JpaRepository<TagReadLog, Long> {

    /**
     * Finds the latest tag read log for a given UID with a pessimistic write lock (SELECT ... FOR UPDATE).
     * This prevents concurrent validation checks for the same UID.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TagReadLog> findFirstByUidOrderByReadCtrDesc(String uid);
}
