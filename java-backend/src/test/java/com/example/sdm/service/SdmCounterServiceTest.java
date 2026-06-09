package com.example.sdm.service;

import com.example.sdm.entity.NfcItemMapping;
import com.example.sdm.entity.TagReadLog;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.repository.NfcItemMappingRepository;
import com.example.sdm.repository.TagReadLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SdmCounterService.class)
class SdmCounterServiceTest {

    @Autowired
    private SdmCounterService sdmCounterService;

    @Autowired
    private TagReadLogRepository tagReadLogRepository;

    @Autowired
    private NfcItemMappingRepository nfcItemMappingRepository;

    @Test
    @DisplayName("최초 태깅 시 카운터 검증 및 로그 저장 성공")
    void verifyAndLog_Success_WhenFirstTime() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        nfcItemMappingRepository.save(new NfcItemMapping(uid, "ITEM100"));
        int readCtr = 1;

        // When
        String status = sdmCounterService.verifyAndLog(uid, readCtr);

        // Then
        assertEquals("FIRST_VALIDATION", status);
        Optional<TagReadLog> latestLog = tagReadLogRepository.findFirstByUidOrderByReadCtrDesc(uid);
        assertTrue(latestLog.isPresent());
        assertEquals(readCtr, latestLog.get().getReadCtr());
        assertEquals(uid, latestLog.get().getUid());
        assertNotNull(latestLog.get().getCreatedAt());
    }

    @Test
    @DisplayName("이전 카운터보다 큰 값으로 태깅 시 성공 및 신규 로그 누적")
    void verifyAndLog_Success_WhenCounterIsGreater() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        nfcItemMappingRepository.save(new NfcItemMapping(uid, "ITEM100"));
        String status1 = sdmCounterService.verifyAndLog(uid, 5); // 첫 번째 태깅 (ctr=5)

        // When
        String status2 = sdmCounterService.verifyAndLog(uid, 10); // 두 번째 태깅 (ctr=10)

        // Then
        assertEquals("FIRST_VALIDATION", status1);
        assertEquals("SUCCESS", status2);
        Optional<TagReadLog> latestLog = tagReadLogRepository.findFirstByUidOrderByReadCtrDesc(uid);
        assertTrue(latestLog.isPresent());
        assertEquals(10, latestLog.get().getReadCtr());

        // 전체 로그 카운트 검증 (이력이 누적되므로 총 2개의 로그가 있어야 함)
        long logCount = tagReadLogRepository.count();
        assertEquals(2, logCount);
    }

    @Test
    @DisplayName("이전 카운터보다 작거나 같은 값으로 태깅 시 예외 발생 (Anti-Replay)")
    void verifyAndLog_ThrowsException_WhenCounterIsLessOrEqual() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        nfcItemMappingRepository.save(new NfcItemMapping(uid, "ITEM100"));
        sdmCounterService.verifyAndLog(uid, 10); // 첫 번째 태깅 (ctr=10)

        // When & Then: 동일한 카운터로 태깅 시 예외 발생해야 함
        IllegalArgumentException sameException = assertThrows(IllegalArgumentException.class, () -> {
            sdmCounterService.verifyAndLog(uid, 10);
        });
        assertEquals("Invalid read_ctr: 이전에 검증된 카운트 값보다 커야합니다.", sameException.getMessage());

        // When & Then: 더 작은 카운터로 태깅 시 예외 발생해야 함
        IllegalArgumentException smallerException = assertThrows(IllegalArgumentException.class, () -> {
            sdmCounterService.verifyAndLog(uid, 9);
        });
        assertEquals("Invalid read_ctr: 이전에 검증된 카운트 값보다 커야합니다.", smallerException.getMessage());
    }

    @Test
    @DisplayName("NFC 매핑 검증 성공 - 등록된 태그와 아이템 코드가 일치하는 경우")
    void verifyTagAndItemMapping_Success() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        String itemCd = "ITEM100";
        nfcItemMappingRepository.save(new NfcItemMapping(uid, itemCd));

        // When & Then
        assertDoesNotThrow(() -> {
            sdmCounterService.verifyTagAndItemMapping(uid, itemCd);
        });
    }

    @Test
    @DisplayName("NFC 매핑 검증 실패 - 등록되지 않은 태그 UID인 경우")
    void verifyTagAndItemMapping_ThrowsTagNotRegisteredException() {
        // Given
        String uid = "UNREGISTERED_UID";
        String itemCd = "ITEM100";

        // When & Then
        TagNotRegisteredException ex = assertThrows(TagNotRegisteredException.class, () -> {
            sdmCounterService.verifyTagAndItemMapping(uid, itemCd);
        });
        assertEquals("등록되지 않은 태그(UID)입니다.", ex.getMessage());
    }

    @Test
    @DisplayName("NFC 매핑 검증 실패 - 등록된 UID이지만 아이템 코드가 다른 경우")
    void verifyTagAndItemMapping_ThrowsItemCodeMismatchException() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        String registeredItemCd = "ITEM100";
        String requestedItemCd = "ITEM999";
        nfcItemMappingRepository.save(new NfcItemMapping(uid, registeredItemCd));

        // When & Then
        ItemCodeMismatchException ex = assertThrows(ItemCodeMismatchException.class, () -> {
            sdmCounterService.verifyTagAndItemMapping(uid, requestedItemCd);
        });
        assertEquals("등록된 태그의 아이템 코드와 일치하지 않습니다.", ex.getMessage());
    }
}
