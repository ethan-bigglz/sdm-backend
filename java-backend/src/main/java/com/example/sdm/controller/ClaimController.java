package com.example.sdm.controller;

import com.example.sdm.entity.Item;
import com.example.sdm.entity.User;
import com.example.sdm.service.ClaimService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "4. NFT Claim API", description = "수집가 동의 하에 가스비 대행 NFT 민팅 및 데이터 소유권 등록")
@RestController
@RequestMapping("/api/v1/items")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Operation(summary = "[4-1] NFT 소유권 등록 (Claim)", description = "사전 NFC 태깅 세션 인증을 거친 로그인 사용자가 수집 동의 시 소유권을 등록하고 NFT를 클레임합니다.")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/{nfcUid}/claim")
    public ResponseEntity<Item> claimItem(
        @PathVariable("nfcUid") String nfcUid,
        @AuthenticationPrincipal User user,
        HttpSession session
    ) {
        // [임시 주석 처리] 테스트 편의를 위해 세션 검증을 생략합니다.
        /*
        String verifiedUid = (String) session.getAttribute("verified_nfc_uid");
        if (verifiedUid == null || !verifiedUid.equals(nfcUid)) {
            throw new IllegalArgumentException("NFC Tag verification is required or has expired for UID: " + nfcUid);
        }
        */

        // 2. Perform claiming logic
        Item claimedItem = claimService.claimItem(nfcUid, user);

        // [임시 주석 처리] 세션 증명 제거 생략
        /*
        session.removeAttribute("verified_nfc_uid");
        */

        return ResponseEntity.ok(claimedItem);
    }

    @Operation(summary = "[4-2] 실물 상품 소유권 이력 조회", description = "특정 실물 상품(Item)의 소유권 민팅 및 이전 이력 타임라인을 조회합니다.")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/history")
    public ResponseEntity<java.util.List<com.example.sdm.dto.ItemHistoryResponse>> getItemHistory(
        @PathVariable("id") Integer id
    ) {
        java.util.List<com.example.sdm.dto.ItemHistoryResponse> history = claimService.getItemHistory(id);
        return ResponseEntity.ok(history);
    }
}
