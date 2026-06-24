package com.example.sdm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "브랜드 등록 요청")
public record BrandCreateRequest(
    @Schema(description = "브랜드 식별자 ID", example = "BIGGLZ_GRP")
    String id,

    @Schema(description = "브랜드 테마 색상 (Hex 코드)", example = "#6B46FF")
    String themeColor,

    @Schema(description = "브랜드 영문 이름", example = "Bigglz")
    String nameEn,

    @Schema(description = "브랜드 한글 이름", example = "비글즈")
    String nameKo,

    @Schema(description = "브랜드 상세 설명", example = "비글즈 캐릭터 브랜드 그룹입니다.")
    String description,

    @Schema(description = "로고 이미지 파일 (PNG, JPG, GIF, WEBP, 최대 10MB)", type = "string", format = "binary")
    MultipartFile logoFile,

    @Schema(description = "커버 이미지 파일 (PNG, JPG, GIF, WEBP, 최대 10MB)", type = "string", format = "binary")
    MultipartFile coverFile
) {
    public BrandCreateRequest {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Brand ID cannot be null or empty.");
        }
        if (themeColor == null || themeColor.isBlank()) {
            throw new IllegalArgumentException("Theme color cannot be null or empty.");
        }
        if (nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("Brand English name cannot be null or empty.");
        }
        if (nameKo == null || nameKo.isBlank()) {
            throw new IllegalArgumentException("Brand Korean name cannot be null or empty.");
        }
    }
}
