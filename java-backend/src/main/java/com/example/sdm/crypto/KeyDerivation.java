package com.example.sdm.crypto;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.digests.SHA512Digest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class KeyDerivation {

    private static final byte[] DIV_CONST1 = hexToBytes("50494343446174614b6579");
    private static final byte[] DIV_CONST2 = hexToBytes("536c6f744d61737465724b6579");
    private static final byte[] DIV_CONST3 = hexToBytes("446976426173654b6579");
    private static final byte[] ZERO_KEY = new byte[16];

    /**
     * PBKDF2-HMAC-SHA512 derivation using Bouncy Castle for raw byte password safety.
     */
    public static byte[] pbkdf2HmacSha512(byte[] password, byte[] salt, int iterations, int keyLengthBytes) {
        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
        gen.init(password, salt, iterations);
        KeyParameter param = (KeyParameter) gen.generateDerivedParameters(keyLengthBytes * 8);
        return param.getKey();
    }

    public static byte[] hmacSha256(byte[] key, byte[] msg, boolean noTrunc) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(msg);
            return noTrunc ? digest : Arrays.copyOf(digest, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    /**
     * UID-diversified key derivation
     */
    public static byte[] deriveTagKey(String mode, byte[] masterKey, byte[] uid, int keyNo) {
        if (Arrays.equals(masterKey, ZERO_KEY)) {
            return ZERO_KEY.clone();
        }

        if ("legacy".equalsIgnoreCase(mode)) {
            // b"key" + uid + bytes([key_no])
            byte[] prefix = "key".getBytes();
            byte[] salt = new byte[prefix.length + uid.length + 1];
            System.arraycopy(prefix, 0, salt, 0, prefix.length);
            System.arraycopy(uid, 0, salt, prefix.length, uid.length);
            salt[salt.length - 1] = (byte) keyNo;

            return pbkdf2HmacSha512(masterKey, salt, 5000, 16);
        } else {
            // DIV_CONST2 + key_no
            byte[] divConst2WithKeyNo = new byte[DIV_CONST2.length + 1];
            System.arraycopy(DIV_CONST2, 0, divConst2WithKeyNo, 0, DIV_CONST2.length);
            divConst2WithKeyNo[divConst2WithKeyNo.length - 1] = (byte) keyNo;

            byte[] cmacKey = hmacSha256(masterKey, divConst2WithKeyNo, false);
            byte[] divBaseKey = hmacSha256(masterKey, DIV_CONST3, true);
            byte[] uidHash = hmacSha256(divBaseKey, uid, false);

            // Input: 0x01 + uidHash
            byte[] cmacInput = new byte[1 + uidHash.length];
            cmacInput[0] = 0x01;
            System.arraycopy(uidHash, 0, cmacInput, 1, uidHash.length);

            CMac cmac = new CMac(new AESEngine());
            cmac.init(new KeyParameter(cmacKey));
            cmac.update(cmacInput, 0, cmacInput.length);
            byte[] out = new byte[cmac.getMacSize()];
            cmac.doFinal(out, 0);

            return out;
        }
    }

    /**
     * Undiversified key derivation
     */
    public static byte[] deriveUndiversifiedKey(String mode, byte[] masterKey, int keyNo) {
        if (Arrays.equals(masterKey, ZERO_KEY)) {
            return ZERO_KEY.clone();
        }

        if ("legacy".equalsIgnoreCase(mode)) {
            // b"key_no_uid" + bytes([key_no])
            byte[] prefix = "key_no_uid".getBytes();
            byte[] salt = new byte[prefix.length + 1];
            System.arraycopy(prefix, 0, salt, 0, prefix.length);
            salt[salt.length - 1] = (byte) keyNo;

            return pbkdf2HmacSha512(masterKey, salt, 5000, 16);
        } else {
            if (keyNo != 1) {
                throw new IllegalArgumentException("Only key #1 can be derived in undiversified mode.");
            }
            return hmacSha256(masterKey, DIV_CONST1, false);
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
}
