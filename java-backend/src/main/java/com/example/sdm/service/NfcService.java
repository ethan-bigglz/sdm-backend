package com.example.sdm.service;

import com.example.sdm.crypto.SdmService;
import com.example.sdm.dto.NfcVerificationResponse;
import com.example.sdm.dto.SdmResult;
import com.example.sdm.entity.Item;
import com.example.sdm.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NfcService {

    private final ItemRepository itemRepository;
    private final SdmService sdmService;
    private final String nfcKey;

    public NfcService(ItemRepository itemRepository, SdmService sdmService, @Value("${app.nfc-key}") String nfcKey) {
        this.itemRepository = itemRepository;
        this.sdmService = sdmService;
        this.nfcKey = nfcKey;
    }

    @Transactional
    public NfcVerificationResponse verifyNfcTag(String uid, String ctr, String cmac) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("NFC UID must not be null or empty.");
        }
        if (ctr == null || ctr.isBlank()) {
            throw new IllegalArgumentException("Counter (ctr) must not be null or empty.");
        }
        if (cmac == null || cmac.isBlank()) {
            throw new IllegalArgumentException("CMAC signature must not be null or empty.");
        }

        // 1. Look up Item by nfc_uid
        Item item = itemRepository.findByNfcUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("NFC Tag with UID '%s' is not registered.".formatted(uid)));

        // 2. Decode hex nfc_key to byte[]
        byte[] nfcKeyBytes = hexToBytes(this.nfcKey);

        // 3. Decode uid and ctr to byte[]
        byte[] uidBytes = hexToBytes(uid);
        
        byte[] ctrBytes;
        int ctrVal;
        try {
            if (ctr.length() == 6) { // 3-byte Hex string
                ctrBytes = hexToBytes(ctr);
                ctrVal = unpackBigEndian3Bytes(ctrBytes);
            } else { // Decimal string
                ctrVal = Integer.parseInt(ctr);
                ctrBytes = packBigEndian3Bytes(ctrVal);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid counter format: " + ctr);
        }

        byte[] cmacBytes = hexToBytes(cmac);

        // 4. Verify AES-CMAC signature
        SdmResult sdmResult = sdmService.validatePlainSunWithKey(uidBytes, ctrBytes, cmacBytes, nfcKeyBytes);
        if (!sdmResult.isValid()) {
            throw new SecurityException("NFC Tag authenticity verification failed: " + sdmResult.errorMsg());
        }

        // 5. Anti-Replay: ctr must be strictly greater than last_ctr in DB
        if (ctrVal <= item.getLastCtr()) {
            throw new IllegalArgumentException("Replay Attack Detected. Scanned counter (%d) is not greater than the last verified counter (%d).".formatted(ctrVal, item.getLastCtr()));
        }

        // 6. Update last_ctr in DB
        item.setLastCtr(ctrVal);
        itemRepository.save(item);

        return new NfcVerificationResponse(
                item.getNfcUid(),
                item.getStatus(),
                item.getNftTokenId(),
                item.getProduct().getId(),
                "NFC Tag verification successful."
        );
    }

    private byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal string");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private byte[] packBigEndian3Bytes(int val) {
        byte[] bytes = new byte[3];
        bytes[0] = (byte) ((val >> 16) & 0xFF);
        bytes[1] = (byte) ((val >> 8) & 0xFF);
        bytes[2] = (byte) (val & 0xFF);
        return bytes;
    }

    private int unpackBigEndian3Bytes(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 16) | ((bytes[1] & 0xFF) << 8) | (bytes[2] & 0xFF);
    }
}
