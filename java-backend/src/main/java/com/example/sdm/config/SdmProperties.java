package com.example.sdm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "sdm")
public record SdmProperties(
    String deriveMode,
    String masterKey,
    String encPiccDataParam,
    String encFileDataParam,
    String uidParam,
    String ctrParam,
    String sdmmacParam,
    boolean requireLrp
) {
    @ConstructorBinding
    public SdmProperties {}

    public byte[] getMasterKeyBytes() {
        return hexToBytes(masterKey);
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[16];
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
