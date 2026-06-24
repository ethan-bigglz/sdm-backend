package com.example.sdm.repository;

import com.example.sdm.entity.ItemHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemHistoryRepository extends JpaRepository<ItemHistory, Integer> {
    List<ItemHistory> findByItemIdOrderByCreatedAtDesc(Integer itemId);
}
