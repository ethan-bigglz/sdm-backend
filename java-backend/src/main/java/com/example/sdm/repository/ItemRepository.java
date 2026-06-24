package com.example.sdm.repository;

import com.example.sdm.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer> {
    Optional<Item> findByNfcUid(String nfcUid);
    Optional<Item> findByNftTokenId(Integer nftTokenId);
    boolean existsByNfcUid(String nfcUid);
    boolean existsByNftTokenId(Integer nftTokenId);
    boolean existsByOwnerIdAndProductId(Integer ownerId, String productId);

    @org.springframework.data.jpa.repository.Query("SELECT MAX(i.nftTokenId) FROM Item i")
    Integer findMaxNftTokenId();

    default Integer findMaxNftTokenIdOrDefault() {
        Integer maxId = findMaxNftTokenId();
        return maxId == null ? -1 : maxId;
    }

    java.util.Optional<Item> findFirstByProductIdAndNfcUidIsNullOrderByNftTokenIdAsc(String productId);
}
