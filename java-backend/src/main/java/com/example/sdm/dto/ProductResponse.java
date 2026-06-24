package com.example.sdm.dto;
 
import com.example.sdm.entity.Product;
import com.example.sdm.entity.ProductTier;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
 
@Schema(description = "상품 템플릿 응답")
public record ProductResponse(
    @Schema(description = "상품 식별자 ID", example = "PROD_001")
    String id,
 
    @Schema(description = "브랜드 식별자 ID", example = "BIGGLZ_GRP")
    String brandId,
 
    @Schema(description = "브랜드 영문 명칭", example = "Bigglz")
    String brandName,
 
    @Schema(description = "상품 등급", example = "NORMAL")
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
    public static ProductResponse from(Product product) {
        if (product == null) {
            return null;
        }
        return new ProductResponse(
            product.getId(),
            product.getBrand() != null ? product.getBrand().getId() : null,
            product.getBrand() != null ? product.getBrand().getNameEn() : null,
            product.getTier(),
            product.getNameEn(),
            product.getNameKo(),
            product.getTotalCount(),
            product.getReleaseDate(),
            product.getIsActive(),
            product.getItemImageUrl()
        );
    }
}
