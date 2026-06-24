package com.example.sdm.repository;

import com.example.sdm.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Integer> {
    List<Transfer> findBySenderId(Integer senderId);
    List<Transfer> findByRecipientId(Integer recipientId);
}
