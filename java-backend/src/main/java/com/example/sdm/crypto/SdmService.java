package com.example.sdm.crypto;

import com.example.sdm.config.SdmProperties;
import com.example.sdm.dto.SdmResult;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

@Service
public class SdmService {

    private final SdmProperties properties;

    public SdmService(SdmProperties properties) {
        this.properties = properties;
    }

    public SdmResult validatePlainSunWithKey(byte[] uid, byte[] readCtr, byte[] sdmmac, byte[] nfcKey) {
        try {
            byte[] readCtrReversed = readCtr.clone();
            reverseArray(readCtrReversed);

            byte[] piccData = new byte[uid.length + readCtrReversed.length];
            System.arraycopy(uid, 0, piccData, 0, uid.length);
            System.arraycopy(readCtrReversed, 0, piccData, uid.length, readCtrReversed.length);

            byte[] calculatedMac = calculateSdmmac(false, nfcKey, piccData, null, "AES");

            if (!Arrays.equals(sdmmac, calculatedMac)) {
                return SdmResult.failure("Invalid signature. Tag might be counterfeit.");
            }

            int ctrValue = unpackBigEndian3Bytes(readCtr);

            return SdmResult.success(
                bytesToHex(uid).toUpperCase(),
                ctrValue,
                "AES",
                null,
                null,
                "",
                "",
                null
            );
        } catch (Exception e) {
            return SdmResult.failure(e.getMessage());
        }
    }

    public SdmResult validatePlainSun(byte[] uid, byte[] readCtr, byte[] sdmmac) {
        try {
            if (properties.requireLrp()) {
                return SdmResult.failure("Invalid encryption mode, expected LRP.");
            }

            byte[] masterKey = properties.getMasterKeyBytes();
            byte[] sdmFileReadKey = KeyDerivation.deriveTagKey(properties.deriveMode(), masterKey, uid, 2);

            // Plaintext CTR mirror comes in big-endian in tagpt, wait, Python reverses it:
            // read_ctr_ba = bytearray(read_ctr); read_ctr_ba.reverse()
            byte[] readCtrReversed = readCtr.clone();
            reverseArray(readCtrReversed);

            byte[] piccData = new byte[uid.length + readCtrReversed.length];
            System.arraycopy(uid, 0, piccData, 0, uid.length);
            System.arraycopy(readCtrReversed, 0, piccData, uid.length, readCtrReversed.length);

            byte[] calculatedMac = calculateSdmmac(false, sdmFileReadKey, piccData, null, "AES");

            if (!Arrays.equals(sdmmac, calculatedMac)) {
                return SdmResult.failure("Invalid signature. Tag might be counterfeit.");
            }

            int ctrValue = unpackBigEndian3Bytes(readCtr);

            return SdmResult.success(
                bytesToHex(uid).toUpperCase(),
                ctrValue,
                "AES",
                null,
                null,
                "",
                "",
                null
            );
        } catch (Exception e) {
            return SdmResult.failure(e.getMessage());
        }
    }

    public SdmResult decryptSunMessage(boolean isBulkParam, byte[] piccEncData, byte[] sdmmac, byte[] encFileData, boolean withTt) {
        try {
            String encMode = getEncryptionMode(piccEncData);

            if (properties.requireLrp() && !"LRP".equalsIgnoreCase(encMode)) {
                return SdmResult.failure("Invalid encryption mode, expected LRP.");
            }

            byte[] masterKey = properties.getMasterKeyBytes();
            byte[] sdmMetaReadKey = KeyDerivation.deriveUndiversifiedKey(properties.deriveMode(), masterKey, 1);

            byte[] plaintext;
            if ("AES".equalsIgnoreCase(encMode)) {
                Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sdmMetaReadKey, "AES"), new IvParameterSpec(new byte[16]));
                plaintext = cipher.doFinal(piccEncData);
            } else {
                byte[] piccRand = Arrays.copyOfRange(piccEncData, 0, 8);
                byte[] piccEncDataStripped = Arrays.copyOfRange(piccEncData, 8, piccEncData.length);
                LrpCipher cipher = new LrpCipher(sdmMetaReadKey, 0, piccRand, false);
                plaintext = cipher.decrypt(piccEncDataStripped);
            }

            // Parse PICC plaintext data
            byte piccDataTag = plaintext[0];
            boolean uidMirroringEn = (piccDataTag & 0x80) == 0x80;
            boolean sdmReadCtrEn = (piccDataTag & 0x40) == 0x40;
            int uidLength = piccDataTag & 0x0F;

            if (uidLength != 0x07) {
                // Fake SDMMAC calculation to avoid timing attacks
                byte[] fakeKey = KeyDerivation.deriveTagKey(properties.deriveMode(), masterKey, new byte[7], 2);
                calculateSdmmac(isBulkParam, fakeKey, new byte[10], encFileData, encMode);
                return SdmResult.failure("Unsupported UID length");
            }

            int index = 1;
            byte[] uid = null;
            if (uidMirroringEn) {
                uid = Arrays.copyOfRange(plaintext, index, index + uidLength);
                index += uidLength;
            }

            byte[] readCtr = null;
            int readCtrNum = 0;
            if (sdmReadCtrEn) {
                readCtr = Arrays.copyOfRange(plaintext, index, index + 3);
                readCtrNum = unpackLittleEndian3Bytes(readCtr);
            }

            if (uid == null) {
                return SdmResult.failure("UID cannot be null.");
            }

            byte[] fileKey = KeyDerivation.deriveTagKey(properties.deriveMode(), masterKey, uid, 2);

            // Reconstruct piccData for SDMMAC calculation
            byte[] piccData;
            if (sdmReadCtrEn) {
                piccData = new byte[uid.length + readCtr.length];
                System.arraycopy(uid, 0, piccData, 0, uid.length);
                System.arraycopy(readCtr, 0, piccData, uid.length, readCtr.length);
            } else {
                piccData = uid;
            }

            byte[] calculatedMac = calculateSdmmac(isBulkParam, fileKey, piccData, encFileData, encMode);
            if (!Arrays.equals(sdmmac, calculatedMac)) {
                return SdmResult.failure("Message is not properly signed - invalid MAC");
            }

            byte[] fileDataBytes = null;
            String fileDataHex = null;
            String fileDataUtf8 = null;

            if (encFileData != null && encFileData.length > 0) {
                if (readCtr == null) {
                    return SdmResult.failure("SDMReadCtr is required to decipher SDMENCFileData.");
                }
                fileDataBytes = decryptFileData(fileKey, piccData, readCtr, encFileData, encMode);
                
                // Parse file data matching Python ParamMode bulk/separated logic
                byte[] fileDataUnpacked = fileDataBytes;
                if (isBulkParam) {
                    int fileDataLen = fileDataBytes[2] & 0xFF;
                    fileDataUnpacked = Arrays.copyOfRange(fileDataBytes, 3, 3 + fileDataLen);
                }

                fileDataHex = bytesToHex(fileDataUnpacked);
                fileDataUtf8 = new String(fileDataUnpacked, StandardCharsets.UTF_8).replaceAll("[^\\p{Print}]", "");
            }

            String ttStatus = "";
            String ttColor = "";
            if (withTt && fileDataBytes != null && fileDataBytes.length >= 2) {
                String ttPermStatus = new String(new byte[]{fileDataBytes[0]}, StandardCharsets.US_ASCII);
                String ttCurStatus = new String(new byte[]{fileDataBytes[1]}, StandardCharsets.US_ASCII);

                if ("C".equals(ttPermStatus) && "C".equals(ttCurStatus)) {
                    ttStatus = "OK (not tampered)";
                    ttColor = "green";
                } else if ("O".equals(ttPermStatus) && "C".equals(ttCurStatus)) {
                    ttStatus = "Tampered! (loop closed)";
                    ttColor = "red";
                } else if ("O".equals(ttPermStatus) && "O".equals(ttCurStatus)) {
                    ttStatus = "Tampered! (loop open)";
                    ttColor = "red";
                } else if ("I".equals(ttPermStatus) && "I".equals(ttCurStatus)) {
                    ttStatus = "Not initialized";
                    ttColor = "orange";
                } else if ("N".equals(ttPermStatus) && "T".equals(ttCurStatus)) {
                    ttStatus = "Not supported by the tag";
                    ttColor = "orange";
                } else {
                    ttStatus = "Unknown";
                    ttColor = "orange";
                }
            }

            String piccDataTagHex = String.format("%02x", piccDataTag).toUpperCase();
            return SdmResult.success(
                bytesToHex(uid).toUpperCase(),
                readCtrNum,
                encMode,
                fileDataHex,
                fileDataUtf8,
                ttStatus,
                ttColor,
                piccDataTagHex
            );

        } catch (Exception e) {
            return SdmResult.failure(e.getMessage());
        }
    }

    public byte[] calculateSdmmac(boolean isBulkParam, byte[] sdmFileReadKey, byte[] piccData, byte[] encFileData, String encMode) {
        byte[] inputData = new byte[0];
        if (encFileData != null && encFileData.length > 0) {
            String hexUpper = bytesToHex(encFileData).toUpperCase();
            String sdmmacParamText = "&" + properties.sdmmacParam() + "=";
            if (isBulkParam || properties.sdmmacParam() == null || properties.sdmmacParam().isEmpty()) {
                sdmmacParamText = "";
            }
            inputData = (hexUpper + sdmmacParamText).getBytes(StandardCharsets.US_ASCII);
        }

        byte[] macDigest;
        if ("AES".equalsIgnoreCase(encMode)) {
            byte[] prefix = new byte[]{(byte) 0x3C, (byte) 0xC3, 0x00, 0x01, 0x00, (byte) 0x80};
            byte[] sv2Raw = new byte[prefix.length + piccData.length];
            System.arraycopy(prefix, 0, sv2Raw, 0, prefix.length);
            System.arraycopy(piccData, 0, sv2Raw, prefix.length, piccData.length);

            int padLen = (16 - (sv2Raw.length % 16)) % 16;
            byte[] sv2 = new byte[sv2Raw.length + padLen];
            System.arraycopy(sv2Raw, 0, sv2, 0, sv2Raw.length);

            CMac cmacC2 = new CMac(new AESEngine());
            cmacC2.init(new KeyParameter(sdmFileReadKey));
            cmacC2.update(sv2, 0, sv2.length);
            byte[] c2Digest = new byte[cmacC2.getMacSize()];
            cmacC2.doFinal(c2Digest, 0);

            CMac cmacSdm = new CMac(new AESEngine());
            cmacSdm.init(new KeyParameter(c2Digest));
            cmacSdm.update(inputData, 0, inputData.length);
            macDigest = new byte[cmacSdm.getMacSize()];
            cmacSdm.doFinal(macDigest, 0);
        } else {
            byte[] prefix = new byte[]{0x00, 0x01, 0x00, (byte) 0x80};
            byte[] sv2Raw = new byte[prefix.length + piccData.length];
            System.arraycopy(prefix, 0, sv2Raw, 0, prefix.length);
            System.arraycopy(piccData, 0, sv2Raw, prefix.length, piccData.length);

            int padLen = (14 - (sv2Raw.length % 16) + 16) % 16;
            byte[] sv2 = new byte[sv2Raw.length + padLen + 2];
            System.arraycopy(sv2Raw, 0, sv2, 0, sv2Raw.length);
            sv2[sv2.length - 2] = (byte) 0x1E;
            sv2[sv2.length - 1] = (byte) 0xE1;

            LrpCipher lrpMaster = new LrpCipher(sdmFileReadKey, 0, null, true);
            byte[] masterKey = lrpMaster.cmac(sv2);

            LrpCipher lrpSessionMacing = new LrpCipher(masterKey, 0, null, true);
            macDigest = lrpSessionMacing.cmac(inputData);
        }

        byte[] truncated = new byte[8];
        for (int i = 0; i < 8; i++) {
            truncated[i] = macDigest[i * 2 + 1];
        }
        return truncated;
    }

    public byte[] decryptFileData(byte[] sdmFileReadKey, byte[] piccData, byte[] readCtr, byte[] encFileData, String encMode) {
        if ("AES".equalsIgnoreCase(encMode)) {
            byte[] prefix = new byte[]{(byte) 0xC3, (byte) 0x3C, 0x00, 0x01, 0x00, (byte) 0x80};
            byte[] sv1Raw = new byte[prefix.length + piccData.length];
            System.arraycopy(prefix, 0, sv1Raw, 0, prefix.length);
            System.arraycopy(piccData, 0, sv1Raw, prefix.length, piccData.length);

            int padLen = (16 - (sv1Raw.length % 16)) % 16;
            byte[] sv1 = new byte[sv1Raw.length + padLen];
            System.arraycopy(sv1Raw, 0, sv1, 0, sv1Raw.length);

            CMac cm = new CMac(new AESEngine());
            cm.init(new KeyParameter(sdmFileReadKey));
            cm.update(sv1, 0, sv1.length);
            byte[] kSesSdmFileReadEnc = new byte[cm.getMacSize()];
            cm.doFinal(kSesSdmFileReadEnc, 0);

            byte[] ecbInput = new byte[16];
            System.arraycopy(readCtr, 0, ecbInput, 0, 3);

            byte[] ive;
            try {
                Cipher ecb = Cipher.getInstance("AES/ECB/NoPadding");
                ecb.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kSesSdmFileReadEnc, "AES"));
                ive = ecb.doFinal(ecbInput);

                Cipher cbc = Cipher.getInstance("AES/CBC/NoPadding");
                cbc.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kSesSdmFileReadEnc, "AES"), new IvParameterSpec(ive));
                return cbc.doFinal(encFileData);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("AES decrypt file data failed", e);
            }
        } else {
            byte[] prefix = new byte[]{0x00, 0x01, 0x00, (byte) 0x80};
            byte[] sv2Raw = new byte[prefix.length + piccData.length];
            System.arraycopy(prefix, 0, sv2Raw, 0, prefix.length);
            System.arraycopy(piccData, 0, sv2Raw, prefix.length, piccData.length);

            int padLen = (14 - (sv2Raw.length % 16) + 16) % 16;
            byte[] sv2 = new byte[sv2Raw.length + padLen + 2];
            System.arraycopy(sv2Raw, 0, sv2, 0, sv2Raw.length);
            sv2[sv2.length - 2] = (byte) 0x1E;
            sv2[sv2.length - 1] = (byte) 0xE1;

            LrpCipher lrpMaster = new LrpCipher(sdmFileReadKey, 0, null, true);
            byte[] masterKey = lrpMaster.cmac(sv2);

            byte[] iv = new byte[16];
            System.arraycopy(readCtr, 0, iv, 0, 3);

            LrpCipher lrpSessionEncing = new LrpCipher(masterKey, 1, iv, false);
            return lrpSessionEncing.decrypt(encFileData);
        }
    }

    private static String getEncryptionMode(byte[] piccEncData) {
        if (piccEncData.length == 16) {
            return "AES";
        } else if (piccEncData.length == 24) {
            return "LRP";
        }
        throw new IllegalArgumentException("Unsupported encryption mode length: " + piccEncData.length);
    }

    private static int unpackLittleEndian3Bytes(byte[] val) {
        return (val[0] & 0xFF) | ((val[1] & 0xFF) << 8) | ((val[2] & 0xFF) << 16);
    }

    private static int unpackBigEndian3Bytes(byte[] val) {
        return ((val[0] & 0xFF) << 16) | ((val[1] & 0xFF) << 8) | (val[2] & 0xFF);
    }

    private static void reverseArray(byte[] array) {
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
