package com.example.sdm.service;

import com.example.sdm.entity.Item;
import com.example.sdm.entity.ItemHistory;
import com.example.sdm.entity.User;
import com.example.sdm.repository.ItemRepository;
import com.example.sdm.repository.ItemHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ClaimService {

    private final ItemRepository itemRepository;
    private final ItemHistoryRepository itemHistoryRepository;
    private final AiServiceClient aiServiceClient;

    public ClaimService(ItemRepository itemRepository, ItemHistoryRepository itemHistoryRepository, AiServiceClient aiServiceClient) {
        this.itemRepository = itemRepository;
        this.itemHistoryRepository = itemHistoryRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional
    public Item claimItem(String nfcUid, User user) {
        Item item = itemRepository.findByNfcUid(nfcUid)
                .orElseThrow(() -> new IllegalArgumentException("Item with NFC UID '%s' not found.".formatted(nfcUid)));

        if (!"unclaimed".equals(item.getStatus()) || item.getOwner() != null) {
            throw new IllegalStateException("Item has already been claimed or is in transfer.");
        }

        // Call AI server to execute the token transfer-claim
        String txHash = aiServiceClient.claimItemOnAiServer(item.getNftTokenId(), user.getEmail());

        // Update ownership and status
        item.setOwner(user);
        item.setStatus("claimed");
        Item savedItem = itemRepository.save(item);

        ItemHistory history = new ItemHistory(savedItem, "MINT", null, user, txHash);
        itemHistoryRepository.save(history);

        return savedItem;
    }

    @Transactional(readOnly = true)
    public java.util.List<com.example.sdm.dto.ItemHistoryResponse> getItemHistory(Integer itemId) {
        return itemHistoryRepository.findByItemIdOrderByCreatedAtDesc(itemId)
                .stream()
                .map(com.example.sdm.dto.ItemHistoryResponse::from)
                .toList();
    }
}
