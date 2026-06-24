package com.example.sdm.controller;

import com.example.sdm.dto.BrandCreateRequest;
import com.example.sdm.dto.ProductCreateRequest;
import com.example.sdm.dto.ProductResponse;
import com.example.sdm.dto.ItemRegisterRequest;
import com.example.sdm.entity.Brand;
import com.example.sdm.entity.Product;
import com.example.sdm.entity.Item;
import com.example.sdm.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1. Admin API", description = "관리자 전용 브랜드, 상품 템플릿 및 실물 상품 관리")
@RestController
@RequestMapping("/api/v1")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "[1-1] 브랜드 등록 (Admin)", description = "신규 브랜드 그룹을 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/admin/brands", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Brand> createBrand(@ModelAttribute BrandCreateRequest request) {
        Brand created = adminService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "[1-2] 상품 템플릿 등록 (Admin)", description = "신규 상품 종류(Product)를 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/products")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody ProductCreateRequest request) {
        Product created = adminService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @Operation(summary = "[1-3] 실물 상품(Item) 등록 (Admin/Factory)", description = "NFC 태그와 NFT 토큰 ID를 매핑한 실물 상품(Item)을 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/items")
    public ResponseEntity<Item> createItem(@RequestBody ItemRegisterRequest request) {
        Item created = adminService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "[1-4] 브랜드 목록 조회 (Public)", description = "등록된 모든 브랜드를 페이징 처리하여 조회합니다.")
    @GetMapping("/brands")
    public ResponseEntity<Page<Brand>> getBrands(@ParameterObject Pageable pageable) {
        Page<Brand> brands = adminService.getBrands(pageable);
        return ResponseEntity.ok(brands);
    }

    @Operation(summary = "[1-5] 상품 템플릿 목록 조회 (Public)", description = "등록된 모든 상품 템플릿을 페이징 처리하여 조회합니다.")
    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(@ParameterObject Pageable pageable) {
        Page<ProductResponse> products = adminService.getProducts(pageable);
        return ResponseEntity.ok(products);
    }
}
