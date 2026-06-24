package com.example.sdm.service;

import com.example.sdm.entity.Brand;
import com.example.sdm.entity.Product;
import com.example.sdm.entity.Item;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.repository.BrandRepository;
import com.example.sdm.repository.ProductRepository;
import com.example.sdm.repository.ItemRepository;
import com.example.sdm.entity.ProductTier;
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
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ItemRepository itemRepository;

    private Item createAndSaveItem(String uid, int tokenId) {
        Brand brand = brandRepository.save(new Brand("BRAND_01", "#6B46FF", "Brand A", "브랜드 A", "Desc", "logo.png", "cover.png"));
        Product product = productRepository.save(new Product("PROD_01", brand, ProductTier.EXCLUSIVE, "Product A", "상품 A", 100, null, true, "image.png"));
        Item item = new Item(product, uid, tokenId, null, "unclaimed");
        return itemRepository.save(item);
    }

    @Test
    @DisplayName("최초 태깅 시 카운터 검증 및 로그 저장 성공")
    void verifyAndLog_Success_WhenFirstTime() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        createAndSaveItem(uid, 100);
        int readCtr = 1;

        // When
        String status = sdmCounterService.verifyAndLog(uid, readCtr);

        // Then
        assertEquals("FIRST_VALIDATION", status);
        Optional<Item> itemOpt = itemRepository.findByNfcUid(uid);
        assertTrue(itemOpt.isPresent());
        assertEquals(readCtr, itemOpt.get().getLastCtr());
    }

    @Test
    @DisplayName("이전 카운터보다 큰 값으로 태깅 시 성공 및 신규 로그 누적")
    void verifyAndLog_Success_WhenCounterIsGreater() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        createAndSaveItem(uid, 100);
        String status1 = sdmCounterService.verifyAndLog(uid, 5); // 첫 번째 태깅 (ctr=5)

        // When
        String status2 = sdmCounterService.verifyAndLog(uid, 10); // 두 번째 태깅 (ctr=10)

        // Then
        assertEquals("FIRST_VALIDATION", status1);
        assertEquals("SUCCESS", status2);
        Optional<Item> itemOpt = itemRepository.findByNfcUid(uid);
        assertTrue(itemOpt.isPresent());
        assertEquals(10, itemOpt.get().getLastCtr());
    }

    @Test
    @DisplayName("이전 카운터보다 작거나 같은 값으로 태깅 시 예외 발생 (Anti-Replay)")
    void verifyAndLog_ThrowsException_WhenCounterIsLessOrEqual() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        createAndSaveItem(uid, 100);
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
        int tokenId = 100;
        createAndSaveItem(uid, tokenId);

        // When & Then
        assertDoesNotThrow(() -> {
            sdmCounterService.verifyTagAndItemMapping(uid, String.valueOf(tokenId));
        });
    }

    @Test
    @DisplayName("NFC 매핑 검증 실패 - 등록되지 않은 태그 UID인 경우")
    void verifyTagAndItemMapping_ThrowsTagNotRegisteredException() {
        // Given
        String uid = "UNREGISTERED_UID";
        String tokenIdStr = "100";

        // When & Then
        TagNotRegisteredException ex = assertThrows(TagNotRegisteredException.class, () -> {
            sdmCounterService.verifyTagAndItemMapping(uid, tokenIdStr);
        });
        assertEquals("등록되지 않은 태그(UID)입니다.", ex.getMessage());
    }

    @Test
    @DisplayName("NFC 매핑 검증 실패 - 등록된 UID이지만 아이템 코드가 다른 경우")
    void verifyTagAndItemMapping_ThrowsItemCodeMismatchException() {
        // Given
        String uid = "04A1B2C3D4E5F6";
        int registeredTokenId = 100;
        String requestedTokenIdStr = "999";
        createAndSaveItem(uid, registeredTokenId);

        // When & Then
        ItemCodeMismatchException ex = assertThrows(ItemCodeMismatchException.class, () -> {
            sdmCounterService.verifyTagAndItemMapping(uid, requestedTokenIdStr);
        });
        assertEquals("등록된 태그의 토큰 ID와 일치하지 않습니다.", ex.getMessage());
    }
}
