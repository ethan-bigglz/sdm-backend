package com.example.sdm.dto;

public record SdmResult(
    String uid,
    int readCtr,
    String encMode,
    String fileData,
    String fileDataUtf8,
    String ttStatus,
    String ttColor,
    boolean isValid,
    String errorMsg,
    String piccDataTag
) {
    public static SdmResult success(String uid, int readCtr, String encMode, String fileData, String fileDataUtf8, String ttStatus, String ttColor, String piccDataTag) {
        return new SdmResult(uid, readCtr, encMode, fileData, fileDataUtf8, ttStatus, ttColor, true, "", piccDataTag);
    }

    public static SdmResult failure(String errorMsg) {
        return new SdmResult("UNKNOWN", 0, "UNKNOWN", null, null, "", "", false, errorMsg, null);
    }
}
