package com.example.sdm.controller;

import com.example.sdm.config.SdmProperties;
import com.example.sdm.crypto.SdmService;
import com.example.sdm.dto.SdmResult;
import com.example.sdm.service.SdmCounterService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.ByteBuffer;
import java.util.Map;

@Controller
public class SdmViewController {

    private final SdmService sdmService;
    private final SdmProperties properties;
    private final SdmCounterService sdmCounterService;
    
    public SdmViewController(SdmService sdmService, SdmProperties properties, SdmCounterService sdmCounterService) {
        this.sdmService = sdmService;
        this.properties = properties;
        this.sdmCounterService = sdmCounterService;
    }

    @GetMapping("/")
    public String sdmMain() {
        return "sdm_main";
    }

    @GetMapping("/webnfc")
    public String sdmWebNfc() {
        return "sdm_webnfc";
    }

    @GetMapping("/tagpt")
    public String tagpt(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String ctr,
            @RequestParam(required = false) String cmac,
            Model model) {
        try {
            if (uid == null || ctr == null || cmac == null) {
                throw new IllegalArgumentException("Missing required parameters.");
            }
            SdmResult result = sdmService.validatePlainSun(hexToBytes(uid), hexToBytes(ctr), hexToBytes(cmac));
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.errorMsg());
            }
            
            model.addAttribute("encryption_mode", result.encMode());
            model.addAttribute("uid", result.uid());
            model.addAttribute("read_ctr_num", result.readCtr());
            return "sdm_info";
        } catch (Exception e) {
            model.addAttribute("code", 400);
            model.addAttribute("msg", e.getMessage());
            return "error";
        }
    }

    @GetMapping("/tagpt2")
    public String tagpt2(
            @RequestParam(required = false) String uid,
            @RequestParam(required = false) String ctr,
            @RequestParam(required = false) String cmac,
            Model model) {
        
        boolean isValid = true;
        String errorMsg = "";
        String uidHex = "UNKNOWN";
        int readCtrNum = 0;
        String encMode = "UNKNOWN";

        try {
            if (uid == null || ctr == null || cmac == null) {
                throw new IllegalArgumentException("Missing parameters.");
            }
            
            byte[] uidBytes = hexToBytes(uid);
            byte[] ctrBytes = hexToBytes(ctr);
            byte[] cmacBytes = hexToBytes(cmac);
            
            SdmResult result = sdmService.validatePlainSun(uidBytes, ctrBytes, cmacBytes);
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.errorMsg());
            }
            
            uidHex = result.uid();
            readCtrNum = result.readCtr();
            encMode = result.encMode();

            // Anti-replay check & DB logging
            sdmCounterService.verifyAndLog(uidHex, readCtrNum);

        } catch (Exception e) {
            isValid = false;
            errorMsg = e.getMessage();
        }

        model.addAttribute("is_valid", isValid);
        model.addAttribute("error_msg", errorMsg);
        model.addAttribute("encryption_mode", encMode);
        model.addAttribute("uid", uidHex);
        model.addAttribute("read_ctr_num", readCtrNum);
        return "tagpt2_genuine";
    }

    @GetMapping("/tag")
    public String tag(
            @RequestParam(required = false) String e,
            @RequestParam(required = false) String picc_data,
            @RequestParam(required = false) String enc,
            @RequestParam(required = false) String cmac,
            Model model) {
        return handleEncryptedSdmRequest(e, picc_data, enc, cmac, false, model);
    }

    @GetMapping("/tagtt")
    public String tagtt(
            @RequestParam(required = false) String e,
            @RequestParam(required = false) String picc_data,
            @RequestParam(required = false) String enc,
            @RequestParam(required = false) String cmac,
            Model model) {
        return handleEncryptedSdmRequest(e, picc_data, enc, cmac, true, model);
    }

    private String handleEncryptedSdmRequest(String e, String piccData, String enc, String cmac, boolean withTt, Model model) {
        try {
            ParamContext ctx = parseParameters(e, piccData, enc, cmac);
            SdmResult result = sdmService.decryptSunMessage(ctx.isBulk(), ctx.piccEncData(), ctx.sdmmac(), ctx.encFileData(), withTt);
            
            if (!result.isValid()) {
                throw new IllegalArgumentException(result.errorMsg());
            }

            model.addAttribute("encryption_mode", result.encMode());
            model.addAttribute("picc_data_tag", result.piccDataTag());
            model.addAttribute("uid", result.uid());
            model.addAttribute("read_ctr_num", result.readCtr());
            model.addAttribute("file_data", result.fileData());
            model.addAttribute("file_data_utf8", result.fileDataUtf8());
            model.addAttribute("tt_status", result.ttStatus());
            model.addAttribute("tt_color", result.ttColor());
            return "sdm_info";

        } catch (Exception err) {
            model.addAttribute("code", 400);
            model.addAttribute("msg", err.getMessage());
            return "error";
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
