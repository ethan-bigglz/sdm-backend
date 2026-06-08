package com.example.sdm.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public class LrpCipher {
    private final byte[] key;
    private final int u;
    private final boolean pad;
    private byte[] r;

    private final byte[][] p;
    private final byte[][] ku;
    private final byte[] kp;

    public LrpCipher(byte[] key, int u, byte[] r, boolean pad) {
        this.key = key.clone();
        this.u = u;
        this.r = r != null ? r.clone() : new byte[16];
        this.pad = pad;

        this.p = generatePlaintexts(this.key);
        this.ku = generateUpdatedKeys(this.key);
        this.kp = this.ku[this.u];
    }

    private static byte[] aesEncrypt(byte[] key, byte[] plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plain);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES encrypt failed in LRP", e);
        }
    }

    private static byte[] aesDecrypt(byte[] key, byte[] cipherText) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES decrypt failed in LRP", e);
        }
    }

    // Algorithm 1
    private static byte[][] generatePlaintexts(byte[] k) {
        byte[] h = aesEncrypt(k, fillBytes((byte) 0x55));
        byte[][] p = new byte[16][];
        for (int i = 0; i < 16; i++) {
            p[i] = aesEncrypt(h, fillBytes((byte) 0xAA));
            h = aesEncrypt(h, fillBytes((byte) 0x55));
        }
        return p;
    }

    // Algorithm 2
    private static byte[][] generateUpdatedKeys(byte[] k) {
        byte[] h = aesEncrypt(k, fillBytes((byte) 0xAA));
        byte[][] uk = new byte[4][];
        for (int i = 0; i < 4; i++) {
            uk[i] = aesEncrypt(h, fillBytes((byte) 0xAA));
            h = aesEncrypt(h, fillBytes((byte) 0x55));
        }
        return uk;
    }

    // Algorithm 3
    public static byte[] evalLrp(byte[][] p, byte[] kp, byte[] x, boolean finalStep) {
        byte[] y = kp.clone();
        int[] nibbles = getNibbles(x);
        for (int idx : nibbles) {
            y = aesEncrypt(y, p[idx]);
        }
        if (finalStep) {
            y = aesEncrypt(y, new byte[16]);
        }
        return y;
    }

    public byte[] cmac(byte[] data) {
        byte[] k0 = evalLrp(this.p, this.kp, new byte[16], true);
        byte[] k1 = multiplyByTwo(k0);
        byte[] k2 = multiplyByTwo(k1);

        byte[] y = new byte[16];
        int len = data.length;
        int blocks = (len + 15) / 16;
        if (blocks == 0) {
            blocks = 1;
        }

        for (int i = 0; i < blocks - 1; i++) {
            byte[] x = Arrays.copyOfRange(data, i * 16, (i + 1) * 16);
            y = evalLrp(this.p, this.kp, xor(x, y), true);
        }

        int lastBlockLen = len - (blocks - 1) * 16;
        byte[] xLast = new byte[16];
        boolean padLast = false;
        if (lastBlockLen < 16) {
            System.arraycopy(data, (blocks - 1) * 16, xLast, 0, lastBlockLen);
            xLast[lastBlockLen] = (byte) 0x80;
            padLast = true;
        } else {
            System.arraycopy(data, (blocks - 1) * 16, xLast, 0, 16);
        }

        y = xor(xLast, y);
        y = xor(y, padLast ? k2 : k1);

        return evalLrp(this.p, this.kp, y, true);
    }

    public byte[] encrypt(byte[] data) {
        byte[] pt;
        if (this.pad) {
            pt = addPadding(data);
        } else {
            if (data.length % 16 != 0) {
                throw new IllegalArgumentException("Length must be multiple of AES block size");
            }
            if (data.length == 0) {
                throw new IllegalArgumentException("Zero length plain text not supported");
            }
            pt = data.clone();
        }

        byte[] ct = new byte[pt.length];
        byte[] currentR = this.r.clone();

        for (int i = 0; i < pt.length; i += 16) {
            byte[] block = Arrays.copyOfRange(pt, i, i + 16);
            byte[] y = evalLrp(this.p, this.kp, currentR, true);
            byte[] encryptedBlock = aesEncrypt(y, block);
            System.arraycopy(encryptedBlock, 0, ct, i, 16);
            currentR = incrCounter(currentR);
        }
        this.r = currentR;
        return ct;
    }

    public byte[] decrypt(byte[] data) {
        if (data.length % 16 != 0) {
            throw new IllegalArgumentException("Ciphertext length must be multiple of 16");
        }

        byte[] pt = new byte[data.length];
        byte[] currentR = this.r.clone();

        for (int i = 0; i < data.length; i += 16) {
            byte[] block = Arrays.copyOfRange(data, i, i + 16);
            byte[] y = evalLrp(this.p, this.kp, currentR, true);
            byte[] decryptedBlock = aesDecrypt(y, block);
            System.arraycopy(decryptedBlock, 0, pt, i, 16);
            currentR = incrCounter(currentR);
        }
        this.r = currentR;

        if (this.pad) {
            return removePadding(pt);
        }
        return pt;
    }

    public static byte[] addPadding(byte[] data) {
        int padLen = 16 - (data.length % 16);
        byte[] padded = new byte[data.length + padLen];
        System.arraycopy(data, 0, padded, 0, data.length);
        padded[data.length] = (byte) 0x80;
        return padded;
    }

    public static byte[] removePadding(byte[] data) {
        int padLen = 0;
        for (int i = data.length - 1; i >= 0; i--) {
            padLen++;
            if (data[i] == (byte) 0x80) {
                break;
            }
            if (data[i] != 0) {
                throw new IllegalArgumentException("Invalid padding");
            }
        }
        return Arrays.copyOf(data, data.length - padLen);
    }

    // GF(2^128) Multiplication by x (0x02)
    private static byte[] multiplyByTwo(byte[] val) {
        byte[] res = new byte[16];
        int carry = 0;
        for (int i = 15; i >= 0; i--) {
            int nextCarry = (val[i] & 0x80) >>> 7;
            res[i] = (byte) ((val[i] << 1) | carry);
            carry = nextCarry;
        }
        if ((val[0] & 0x80) != 0) {
            res[15] ^= 0x87;
        }
        return res;
    }

    public static byte[] incrCounter(byte[] r) {
        byte[] res = r.clone();
        for (int i = res.length - 1; i >= 0; i--) {
            res[i]++;
            if (res[i] != 0) {
                return res;
            }
        }
        return new byte[r.length];
    }

    private static int[] getNibbles(byte[] bytes) {
        int[] nibbles = new int[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            nibbles[i * 2] = (bytes[i] & 0xF0) >>> 4;
            nibbles[i * 2 + 1] = bytes[i] & 0x0F;
        }
        return nibbles;
    }

    private static byte[] fillBytes(byte value) {
        byte[] b = new byte[16];
        Arrays.fill(b, value);
        return b;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] res = new byte[16];
        for (int i = 0; i < 16; i++) {
            res[i] = (byte) (a[i] ^ b[i]);
        }
        return res;
    }
}
