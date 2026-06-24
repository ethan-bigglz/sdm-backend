package com.example.sdm.controller;

import com.example.sdm.dto.NfcVerificationResponse;
import com.example.sdm.service.NfcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * NFC 태깅 검증 API 컨트롤러
 */
@Tag(name = "3. NFC Verification API", description = "실물 NFC 태깅 진위 여부 검증 및 세션 바인딩")
@RestController
@RequestMapping("/api/v1/nfc")
public class NfcController {

    private final NfcService nfcService;

    public NfcController(NfcService nfcService) {
        this.nfcService = nfcService;
    }

    /**
     * 실물 태그의 고유 식별자(uid) 및 변조 차단 서명(cmac), 카운터(ctr)를 검증합니다.
     * 검증 완료 후, 후속 NFT 민팅 시 인증 여부를 대조하기 위해 해당 NFC UID를 세션에 보관합니다.
     */
    @Operation(summary = "[3-1] 실물 태그 복제 방지 검증", description = "NTAG 424 DNA 보안 키와 CMAC 서명, 카운터를 검증하여 세션 권한을 발급합니다.")
    @GetMapping("/verify")
    public ResponseEntity<NfcVerificationResponse> verifyNfcTag(
            @Parameter(description = "NFC UID 16진수 문자열", example = "UID1234567890")
            @RequestParam("uid") String uid,

            @Parameter(description = "NFC 읽기 카운트 값 (1 이상)", example = "1")
            @RequestParam("ctr") String ctr,

            @Parameter(description = "NTAG 424 DNA 유도 CMAC 서명값", example = "dummy_cmac")
            @RequestParam("cmac") String cmac,
            
            HttpSession session) {

        NfcVerificationResponse response = nfcService.verifyNfcTag(uid, ctr, cmac);

        // 검증 성공 시 세션에 인증된 NFC UID 기록하여 안전하게 트랜잭션 연속성 보장
        session.setAttribute("verified_nfc_uid", response.nfcUid());

        return ResponseEntity.ok(response);
    }
}
