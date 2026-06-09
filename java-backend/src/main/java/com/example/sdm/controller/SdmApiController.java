package com.example.sdm.controller;

import com.example.sdm.config.SdmProperties;
import com.example.sdm.crypto.SdmService;
import com.example.sdm.dto.SdmResult;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.service.SdmCounterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.util.Map;

@Tag(name = "SDM API", description = "Secure Dynamic Messaging (NFC NTAG 424 DNA SUN) 검증용 API")
@RestController
@RequestMapping("/api")
public class SdmApiController {

    private final SdmService sdmService;
    private final SdmProperties properties;
    private final SdmCounterService sdmCounterService;
    
    public SdmApiController(SdmService sdmService, SdmProperties properties, SdmCounterService sdmCounterService) {
        this.sdmService = sdmService;
        this.properties = properties;
        this.sdmCounterService = sdmCounterService;
    }

    @Operation(summary = "평문 SUN 데이터 검증 (기본)", description = "NFC 태그에서 전달된 평문 UID, 읽기 카운터(CTR), 서명(CMAC) 값을 사용해 해당 태그의 무결성 및 정품 여부를 검증합니다. (DB 이력 기록 안 함)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "검증 성공"),
        @ApiResponse(responseCode = "400", description = "파라미터 누락 또는 검증 실패")
    })
    @GetMapping("/tagpt")
    public ResponseEntity<?> tagptApi(
            @Parameter(description = "NFC 태그 고유 식별자 (Hex 문자열)", required = true) @RequestParam(required = false) String uid,
            @Parameter(description = "NFC 읽기 횟수 카운터 (Hex 문자열)", required = true) @RequestParam(required = false) String ctr,
            @Parameter(description = "암호화 서명 값 (Hex 문자열)", required = true) @RequestParam(required = false) String cmac) {
        try {
            if (uid == null || ctr == null || cmac == null) {
                throw new IllegalArgumentException("Missing required parameters.");
            }
            SdmResult result = sdmService.validatePlainSun(hexToBytes(uid), hexToBytes(ctr), hexToBytes(cmac));
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.errorMsg());
            }

            return ResponseEntity.ok(Map.of(
                "uid", result.uid(),
                "read_ctr", result.readCtr(),
                "enc_mode", result.encMode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "평문 SUN 데이터 검증 및 중복 체크 (Anti-Replay)", description = "평문 데이터(UID, CTR, CMAC)를 검증하고, 동일한 카운터를 다시 사용하는 재생 공격(Replay Attack)을 방지하기 위해 DB에 검증 로그를 적재하고 카운터가 증가했는지 확인합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "검증 성공 (최초 또는 성공 이력 포함)"),
        @ApiResponse(responseCode = "400", description = "파라미터 누락, 검증 실패 또는 이미 검증된 동일/이전 카운터 값 오류 (Replay Attack 감지)")
    })
    @GetMapping("/tagpt2")
    public ResponseEntity<?> tagpt2Api(
            @Parameter(description = "NFC 태그 고유 식별자 (Hex 문자열)", required = true) @RequestParam(required = false) String uid,
            @Parameter(description = "NFC 읽기 횟수 카운터 (Hex 문자열)", required = true) @RequestParam(required = false) String ctr,
            @Parameter(description = "암호화 서명 값 (Hex 문자열)", required = true) @RequestParam(required = false) String cmac) {
        
        String uidHex = (uid != null) ? uid : "UNKNOWN";
        int readCtrNum = 0;
        String encMode = "UNKNOWN";
        
        try {
            if (uid == null || ctr == null || cmac == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", "필수 파라미터가 누락되었습니다. (uid, ctr, cmac 필요)",
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            }
            
            byte[] uidBytes = hexToBytes(uid);
            byte[] ctrBytes = hexToBytes(ctr);
            byte[] cmacBytes = hexToBytes(cmac);
            
            SdmResult result = sdmService.validatePlainSun(uidBytes, ctrBytes, cmacBytes);
            if (!result.isValid()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", "검증 실패: " + result.errorMsg(),
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            }
            
            uidHex = result.uid();
            readCtrNum = result.readCtr();
            encMode = result.encMode();
            
            // DB에 검증 로그 기록 및 상태 획득 (최초 검증 / 일반 검증)
            String status = sdmCounterService.verifyAndLog(uidHex, readCtrNum);
            
            String message = status.equals("FIRST_VALIDATION") 
                    ? "최초 검증 성공 (신규 이력 등록 완료)" 
                    : "검증 성공 (이전 이력 존재)";
            
            return ResponseEntity.ok(Map.of(
                "status", status,
                "message", message,
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
            
        } catch (IllegalArgumentException e) {
            // Replay attack 또는 카운터 중복 검증 실패 예외
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILURE",
                "message", "검증 실패: " + e.getMessage(),
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
        } catch (Exception e) {
            // 기타 시스템 에러
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILURE",
                "message", "시스템 에러: " + e.getMessage(),
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
        }
    }

    @Operation(summary = "아이템 코드 매핑 및 평문 SUN 데이터 통합 검증", description = "태그가 시스템(DB)에 사전에 등록된 아이템 코드와 올바르게 매핑되어 있는지 확인한 다음, 평문 데이터(UID, CTR, CMAC) 검증 및 중복 체크(Anti-Replay)를 일괄로 수행합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "모든 검증 완료 및 이력 저장 성공"),
        @ApiResponse(responseCode = "400", description = "파라미터 누락, 미등록 태그, 아이템 코드 불일치, 암호화 검증 실패 또는 중복 태깅 오류")
    })
    @GetMapping("/tag2")
    public ResponseEntity<?> tag2Api(
            @Parameter(description = "NFC 태그 고유 식별자 (Hex 문자열)", required = true) @RequestParam(required = false) String uid,
            @Parameter(description = "연동할 아이템 코드 (상품 식별자 등)", required = true) @RequestParam(required = false) String item_cd,
            @Parameter(description = "NFC 읽기 횟수 카운터 (Integer)", required = true) @RequestParam(required = false) String ctr,
            @Parameter(description = "암호화 서명 값 (Hex 문자열)", required = true) @RequestParam(required = false) String cmac) {
        
        String uidHex = (uid != null) ? uid : "UNKNOWN";
        int readCtrNum = 0;
        String encMode = "UNKNOWN";
        
        try {
            if (uid == null || item_cd == null || ctr == null || cmac == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", "필수 파라미터가 누락되었습니다. (uid, item_cd, ctr, cmac 필요)",
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            }
            
            // 1. nfc_item_mapping validation check
            try {
                sdmCounterService.verifyTagAndItemMapping(uid, item_cd);
            } catch (TagNotRegisteredException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", e.getMessage(),
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            } catch (ItemCodeMismatchException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", e.getMessage(),
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            }
            
            // 2. Cryptographic Plain SUN Validation
            byte[] uidBytes = hexToBytes(uid);
            byte[] ctrBytes = hexToBytes(ctr);
            byte[] cmacBytes = hexToBytes(cmac);
            
            SdmResult result = sdmService.validatePlainSun(uidBytes, ctrBytes, cmacBytes);
            if (!result.isValid()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "FAILURE",
                    "message", "검증 실패: " + result.errorMsg(),
                    "uid", uidHex,
                    "read_ctr", readCtrNum,
                    "enc_mode", encMode
                ));
            }
            
            uidHex = result.uid();
            readCtrNum = result.readCtr();
            encMode = result.encMode();
            
            // 3. Replay attack check & DB logging
            String status = sdmCounterService.verifyAndLog(uidHex, readCtrNum);
            
            String message = status.equals("FIRST_VALIDATION") 
                    ? "최초 검증 성공 (신규 이력 등록 완료)" 
                    : "검증 성공 (이전 이력 존재)";
            
            return ResponseEntity.ok(Map.of(
                "status", status,
                "message", message,
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
            
        } catch (IllegalArgumentException e) {
            // Replay attack 또는 카운터 중복 검증 실패 예외
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILURE",
                "message", "검증 실패: " + e.getMessage(),
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
        } catch (Exception e) {
            // 기타 시스템 에러
            return ResponseEntity.badRequest().body(Map.of(
                "status", "FAILURE",
                "message", "시스템 에러: " + e.getMessage(),
                "uid", uidHex,
                "read_ctr", readCtrNum,
                "enc_mode", encMode
            ));
        }
    }

    @Operation(summary = "암호화된 SUN 데이터 복호화 및 검증 (기본)", description = "PICC 데이터(태그 UID 및 카운터가 암호화된 값), 서명(CMAC), 암호화 파일 데이터를 전달받아 복호화하고 정품 여부를 검증합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "복호화 및 검증 성공, 복호화된 UID 및 카운터 정보 반환"),
        @ApiResponse(responseCode = "400", description = "파라미터 누락, 복호화 실패 또는 비정상 서명")
    })
    @GetMapping("/tag")
    public ResponseEntity<?> tagApi(
            @Parameter(description = "일괄 암호화 파라미터 (PICC 데이터 + enc 파일 데이터 + CMAC 전체 결합 Hex 문자열)", required = false) @RequestParam(required = false) String e,
            @Parameter(description = "암호화된 PICC 데이터 (Hex 문자열)", required = false) @RequestParam(required = false) String picc_data,
            @Parameter(description = "암호화된 파일 데이터 (Hex 문자열)", required = false) @RequestParam(required = false) String enc,
            @Parameter(description = "암호화 서명 값 (Hex 문자열)", required = false) @RequestParam(required = false) String cmac) {
        return handleEncryptedSdmApiRequest(e, picc_data, enc, cmac, false);
    }

    @Operation(summary = "암호화된 SUN 데이터 복호화 및 TagTamper 감지", description = "암호화된 PICC 데이터, 서명, 암호화 파일 데이터를 복호화 및 검증하며, 추가적으로 태그의 루프 훼손 여부(TagTamper 상태)를 복호화된 파일데이터에서 추출하여 반환합니다.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "복호화 성공 및 루프 단선/훼손 상태 정보 반환"),
        @ApiResponse(responseCode = "400", description = "파라미터 누락, 복호화 실패 또는 비정상 서명")
    })
    @GetMapping("/tagtt")
    public ResponseEntity<?> tagttApi(
            @Parameter(description = "일괄 암호화 파라미터 (PICC 데이터 + enc 파일 데이터 + CMAC 전체 결합 Hex 문자열)", required = false) @RequestParam(required = false) String e,
            @Parameter(description = "암호화된 PICC 데이터 (Hex 문자열)", required = false) @RequestParam(required = false) String picc_data,
            @Parameter(description = "암호화된 파일 데이터 (Hex 문자열)", required = false) @RequestParam(required = false) String enc,
            @Parameter(description = "암호화 서명 값 (Hex 문자열)", required = false) @RequestParam(required = false) String cmac) {
        return handleEncryptedSdmApiRequest(e, picc_data, enc, cmac, true);
    }

    private ResponseEntity<?> handleEncryptedSdmApiRequest(String e, String piccData, String enc, String cmac, boolean withTt) {
        try {
            ParamContext ctx = parseParameters(e, piccData, enc, cmac);
            SdmResult result = sdmService.decryptSunMessage(ctx.isBulk(), ctx.piccEncData(), ctx.sdmmac(), ctx.encFileData(), withTt);
            
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.errorMsg());
            }

            return ResponseEntity.ok(Map.of(
                "uid", result.uid(),
                "file_data", result.fileData() != null ? result.fileData() : Map.of(),
                "read_ctr", result.readCtr(),
                "tt_status", result.ttStatus(),
                "enc_mode", result.encMode()
            ));

        } catch (Exception err) {
            return ResponseEntity.badRequest().body(Map.of("error", err.getMessage()));
        }
    }

    private ParamContext parseParameters(String eParam, String piccDataParam, String encParam, String cmacParam) {
        if (eParam != null && !eParam.isEmpty()) {
            byte[] eb = hexToBytes(eParam);
            ByteBuffer buf = ByteBuffer.wrap(eb);
            int len = eb.length;
            byte[] piccEncData;
            byte[] encFileData = null;
            byte[] sdmmac;

            if ((len - 8) % 16 == 0) {
                piccEncData = new byte[16];
                buf.get(piccEncData);
                int fileLen = len - 16 - 8;
                if (fileLen > 0) {
                    encFileData = new byte[fileLen];
                    buf.get(encFileData);
                }
                sdmmac = new byte[8];
                buf.get(sdmmac);
            } else if ((len - 8) % 16 == 8) {
                piccEncData = new byte[24];
                buf.get(piccEncData);
                int fileLen = len - 24 - 8;
                if (fileLen > 0) {
                    encFileData = new byte[fileLen];
                    buf.get(encFileData);
                }
                sdmmac = new byte[8];
                buf.get(sdmmac);
            } else {
                throw new IllegalArgumentException("Incorrect length of the dynamic parameter.");
            }
            return new ParamContext(true, piccEncData, encFileData, sdmmac);
        } else {
            String pData = piccDataParam != null ? piccDataParam : "";
            String pCmac = cmacParam != null ? cmacParam : "";
            
            if (pData.isEmpty()) {
                throw new IllegalArgumentException("Parameter " + properties.encPiccDataParam() + " is required");
            }
            if (pCmac.isEmpty()) {
                throw new IllegalArgumentException("Parameter " + properties.sdmmacParam() + " is required");
            }
            byte[] piccEncData = hexToBytes(pData);
            byte[] sdmmac = hexToBytes(pCmac);
            byte[] encFileData = (encParam != null && !encParam.isEmpty()) ? hexToBytes(encParam) : null;
            return new ParamContext(false, piccEncData, encFileData, sdmmac);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private record ParamContext(
        boolean isBulk,
        byte[] piccEncData,
        byte[] encFileData,
        byte[] sdmmac
    ) {}
}
