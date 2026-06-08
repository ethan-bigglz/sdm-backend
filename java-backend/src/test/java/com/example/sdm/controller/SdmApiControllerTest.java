package com.example.sdm.controller;

import com.example.sdm.config.SdmProperties;
import com.example.sdm.crypto.SdmService;
import com.example.sdm.dto.SdmResult;
import com.example.sdm.exception.ItemCodeMismatchException;
import com.example.sdm.exception.TagNotRegisteredException;
import com.example.sdm.service.SdmCounterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SdmApiController.class)
class SdmApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SdmService sdmService;

    @MockitoBean
    private SdmProperties sdmProperties;

    @MockitoBean
    private SdmCounterService sdmCounterService;

    @Test
    @DisplayName("tag2 API - 필수 파라미터 누락 시 400 Bad Request 리턴")
    void tag2Api_MissingParameters_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/tag2")
                        .param("uid", "04A1B2C3D4E5F6")
                        .param("item_cd", "ITEM100")
                        // ctr and cmac missing
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("필수 파라미터가 누락되었습니다. (uid, item_cd, ctr, cmac 필요)"));
    }

    @Test
    @DisplayName("tag2 API - 등록되지 않은 태그 UID 시 400 Bad Request 리턴")
    void tag2Api_UnregisteredUid_ReturnsBadRequest() throws Exception {
        Mockito.doThrow(new TagNotRegisteredException("등록되지 않은 태그(UID)입니다."))
                .when(sdmCounterService).verifyTagAndItemMapping("04A1B2C3D4E5F6", "ITEM100");

        mockMvc.perform(get("/api/tag2")
                        .param("uid", "04A1B2C3D4E5F6")
                        .param("item_cd", "ITEM100")
                        .param("ctr", "000001")
                        .param("cmac", "ABCDEF0123456789")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("등록되지 않은 태그(UID)입니다."));
    }

    @Test
    @DisplayName("tag2 API - 아이템 코드 불일치 시 400 Bad Request 리턴")
    void tag2Api_ItemCodeMismatch_ReturnsBadRequest() throws Exception {
        Mockito.doThrow(new ItemCodeMismatchException("등록된 태그의 아이템 코드와 일치하지 않습니다."))
                .when(sdmCounterService).verifyTagAndItemMapping("04A1B2C3D4E5F6", "ITEM999");

        mockMvc.perform(get("/api/tag2")
                        .param("uid", "04A1B2C3D4E5F6")
                        .param("item_cd", "ITEM999")
                        .param("ctr", "000001")
                        .param("cmac", "ABCDEF0123456789")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.message").value("등록된 태그의 아이템 코드와 일치하지 않습니다."));
    }

    @Test
    @DisplayName("tag2 API - 유효한 요청 시 200 OK와 함께 성공 리턴")
    void tag2Api_Success_ReturnsOk() throws Exception {
        Mockito.doNothing().when(sdmCounterService).verifyTagAndItemMapping("04A1B2C3D4E5F6", "ITEM100");

        SdmResult sdmResult = SdmResult.success("04A1B2C3D4E5F6", 1, "AES", null, null, null, null, null);
        Mockito.when(sdmService.validatePlainSun(any(), any(), any())).thenReturn(sdmResult);

        Mockito.when(sdmCounterService.verifyAndLog("04A1B2C3D4E5F6", 1)).thenReturn("FIRST_VALIDATION");

        mockMvc.perform(get("/api/tag2")
                        .param("uid", "04A1B2C3D4E5F6")
                        .param("item_cd", "ITEM100")
                        .param("ctr", "000001")
                        .param("cmac", "ABCDEF0123456789")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FIRST_VALIDATION"))
                .andExpect(jsonPath("$.message").value("최초 검증 성공 (신규 이력 등록 완료)"))
                .andExpect(jsonPath("$.uid").value("04A1B2C3D4E5F6"))
                .andExpect(jsonPath("$.read_ctr").value(1));
    }
}
