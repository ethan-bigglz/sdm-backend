package com.example.sdm.controller;

import com.example.sdm.config.SdmProperties;
import com.example.sdm.crypto.SdmService;
import com.example.sdm.dto.SdmResult;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.service.SdmCounterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.ByteBuffer;
import java.util.Map;

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

    @GetMapping("/tagpt")
    public ResponseEntity<?> tagptApi(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String ctr,
            @RequestParam(required = false) String cmac) {
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

    @GetMapping("/tagpt2")
    public ResponseEntity<?> tagpt2Api(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String ctr,
            @RequestParam(required = false) String cmac) {
        
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

    @GetMapping("/tag2")
    public ResponseEntity<?> tag2Api(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String item_cd,
            @RequestParam(required = false) String ctr,
            @RequestParam(required = false) String cmac) {
        
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

    @GetMapping("/tag")
    public ResponseEntity<?> tagApi(
            @RequestParam(required = false) String e,
            @RequestParam(required = false) String picc_data,
            @RequestParam(required = false) String enc,
            @RequestParam(required = false) String cmac) {
        return handleEncryptedSdmApiRequest(e, picc_data, enc, cmac, false);
    }

    @GetMapping("/tagtt")
    public ResponseEntity<?> tagttApi(
            @RequestParam(required = false) String e,
            @RequestParam(required = false) String picc_data,
            @RequestParam(required = false) String enc,
            @RequestParam(required = false) String cmac) {
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
