package com.example.sdm.controller;

import com.example.sdm.config.SdmProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final SdmProperties properties;

    public GlobalControllerAdvice(SdmProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("demo_mode")
    public boolean demoMode() {
        byte[] key = properties.getMasterKeyBytes();
        if (key == null || key.length != 16) {
            return false;
        }
        for (byte b : key) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
