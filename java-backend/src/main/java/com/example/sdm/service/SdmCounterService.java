package com.example.sdm.service;

import com.example.sdm.entity.Item;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SdmCounterService {

    private final ItemRepository itemRepository;

    public SdmCounterService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Verifies that the tag UID is registered and matches the provided token ID.
     */
    @Transactional(readOnly = true)
    public void verifyTagAndItemMapping(String uid, String tokenIdStr) {
        if (uid == null || tokenIdStr == null) {
            throw new IllegalArgumentException("UID and Token ID must not be null.");
        }
        
        String normalizedUid = uid.trim().toUpperCase();
        Optional<Item> itemOpt = itemRepository.findByNfcUid(normalizedUid);
        
        if (itemOpt.isEmpty()) {
            throw new TagNotRegisteredException("등록되지 않은 태그(UID)입니다.");
        }
        
        Item item = itemOpt.get();
        
        int tokenId;
        try {
            tokenId = Integer.parseInt(tokenIdStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Token ID format: " + tokenIdStr);
        }

        if (item.getNftTokenId() != tokenId) {
            throw new ItemCodeMismatchException("등록된 태그의 토큰 ID와 일치하지 않습니다.");
        }
    }

    /**
     * Verifies the read counter against the database history and logs the tagging attempt.
     */
    @Transactional
    public String verifyAndLog(String uid, int readCtr) {
        if (uid == null) {
            throw new IllegalArgumentException("UID must not be null.");
        }

        String normalizedUid = uid.trim().toUpperCase();

        Item item = itemRepository.findByNfcUid(normalizedUid)
                .orElseThrow(() -> new TagNotRegisteredException("등록되지 않은 태그(UID)입니다."));

        boolean isFirst = (item.getLastCtr() == 0);
        if (readCtr <= item.getLastCtr()) {
            throw new IllegalArgumentException("Invalid read_ctr: 이전에 검증된 카운트 값보다 커야합니다.");
        }

        item.setLastCtr(readCtr);
        itemRepository.save(item);

        return isFirst ? "FIRST_VALIDATION" : "SUCCESS";
    }
}
