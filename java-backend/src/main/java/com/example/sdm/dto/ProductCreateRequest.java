package com.example.sdm.dto;

import com.example.sdm.entity.ProductTier;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "상품 템플릿 등록 요청")
public record ProductCreateRequest(
    @Schema(description = "상품 식별자 ID", example = "PROD_001")
    String id,

    @Schema(description = "브랜드 식별자 ID", example = "BIGGLZ_GRP")
    String brandId,

    @Schema(description = "상품 등급 (NORMAL, LIMITED, EXCLUSIVE)", example = "NORMAL")
    ProductTier tier,

    @Schema(description = "상품 영문 명칭", example = "Premium Gold Watch")
    String nameEn,

    @Schema(description = "상품 한글 명칭", example = "프리미엄 골드 워치")
    String nameKo,

    @Schema(description = "총 발행 수량", example = "1000")
    Integer totalCount,

    @Schema(description = "출시 날짜 (yyyy-MM-dd)", example = "2026-06-19")
    LocalDate releaseDate,

    @Schema(description = "상품 노출 활성화 여부", example = "true")
    Boolean isActive,

    @Schema(description = "상품 이미지 URL", example = "/uploads/products/prod_001.png")
    String itemImageUrl
) {
    @JsonCreator
    public ProductCreateRequest(
        @JsonProperty("id") String id,
        @JsonProperty("brandId") String brandId,
        @JsonProperty("tier") ProductTier tier,
        @JsonProperty("nameEn") String nameEn,
        @JsonProperty("nameKo") String nameKo,
        @JsonProperty("totalCount") Integer totalCount,
        @JsonProperty("releaseDate") LocalDate releaseDate,
        @JsonProperty("isActive") Boolean isActive,
        @JsonProperty("itemImageUrl") String itemImageUrl
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty.");
        }
        if (brandId == null || brandId.isBlank()) {
            throw new IllegalArgumentException("Brand ID cannot be null or empty.");
        }
        if (tier == null) {
            throw new IllegalArgumentException("Tier cannot be null.");
        }
        if (nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("English name cannot be null or empty.");
        }
        if (nameKo == null || nameKo.isBlank()) {
            throw new IllegalArgumentException("Korean name cannot be null or empty.");
        }
        if (totalCount == null || totalCount <= 0) {
            throw new IllegalArgumentException("Total count must be greater than zero.");
        }
        this.id = id;
        this.brandId = brandId;
        this.tier = tier;
        this.nameEn = nameEn;
        this.nameKo = nameKo;
        this.totalCount = totalCount;
        this.releaseDate = releaseDate;
        this.isActive = (isActive == null) ? true : isActive;
        this.itemImageUrl = itemImageUrl;
    }
}
