package com.example.sdm.service;

import com.example.sdm.entity.NfcItemMapping;
import com.example.sdm.entity.TagReadLog;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.repository.NfcItemMappingRepository;
import com.example.sdm.repository.TagReadLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SdmCounterService {

    private final TagReadLogRepository tagReadLogRepository;
    private final NfcItemMappingRepository nfcItemMappingRepository;

    public SdmCounterService(TagReadLogRepository tagReadLogRepository, NfcItemMappingRepository nfcItemMappingRepository) {
        this.tagReadLogRepository = tagReadLogRepository;
        this.nfcItemMappingRepository = nfcItemMappingRepository;
    }

    /**
     * Verifies that the tag UID is registered and matches the provided item code.
     * 
     * @param uid    the NFC tag UID in hex format
     * @param itemCd the item code to check
     * @throws TagNotRegisteredException if the UID is not found in nfc_item_mapping
     * @throws ItemCodeMismatchException if the UID is found but matches a different item code
     */
    @Transactional(readOnly = true)
    public void verifyTagAndItemMapping(String uid, String itemCd) {
        if (uid == null || itemCd == null) {
            throw new IllegalArgumentException("UID and Item Code must not be null.");
        }
        
        String normalizedUid = uid.trim().toUpperCase();
        Optional<NfcItemMapping> mappingOpt = nfcItemMappingRepository.findByUid(normalizedUid);
        
        if (mappingOpt.isEmpty()) {
            throw new TagNotRegisteredException("등록되지 않은 태그(UID)입니다.");
        }
        
        NfcItemMapping mapping = mappingOpt.get();
        if (!mapping.getItemCd().trim().equalsIgnoreCase(itemCd.trim())) {
            throw new ItemCodeMismatchException("등록된 태그의 아이템 코드와 일치하지 않습니다.");
        }
    }

    /**
     * Verifies the read counter against the database history and logs the tagging attempt.
     * Uses a pessimistic write lock to handle concurrent requests for the same UID safely.
     *
     * @param uid     the NFC tag UID in hex format
     * @param readCtr the read counter from the NFC tag
     * @throws IllegalArgumentException if the counter is not strictly greater than the previously saved value
     */
    @Transactional
    public String verifyAndLog(String uid, int readCtr) {
        // Fetch the latest log for this UID with a pessimistic write lock
        Optional<TagReadLog> latestLogOpt = tagReadLogRepository.findFirstByUidOrderByReadCtrDesc(uid);

        boolean isFirst = true;
        if (latestLogOpt.isPresent()) {
            isFirst = false;
            TagReadLog latestLog = latestLogOpt.get();
            if (readCtr <= latestLog.getReadCtr()) {
                throw new IllegalArgumentException("Invalid read_ctr: 이전에 검증된 카운트 값보다 커야합니다.");
            }
        }

        // Save the new read log
        TagReadLog newLog = new TagReadLog(uid, readCtr);
        tagReadLogRepository.save(newLog);

        return isFirst ? "FIRST_VALIDATION" : "SUCCESS";
    }
}
