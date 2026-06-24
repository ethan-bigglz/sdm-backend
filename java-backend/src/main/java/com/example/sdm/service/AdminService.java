package com.example.sdm.service;

import com.example.sdm.dto.BrandCreateRequest;
import com.example.sdm.dto.ProductCreateRequest;
import com.example.sdm.dto.ItemRegisterRequest;
import com.example.sdm.entity.Brand;
import com.example.sdm.entity.Product;
import com.example.sdm.entity.Item;
import com.example.sdm.repository.BrandRepository;
import com.example.sdm.repository.ProductRepository;
import com.example.sdm.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.example.sdm.dto.ProductResponse;
import com.example.sdm.service.AiServiceClient;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class AdminService {

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ItemRepository itemRepository;
    private final AiServiceClient aiServiceClient;

    public AdminService(BrandRepository brandRepository, ProductRepository productRepository, ItemRepository itemRepository, AiServiceClient aiServiceClient) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.itemRepository = itemRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional
    public Brand createBrand(BrandCreateRequest request) {
        if (brandRepository.existsById(request.id())) {
            throw new IllegalArgumentException("Brand with ID '%s' already exists.".formatted(request.id()));
        }

        String logoPath = uploadImage(request.logoFile(), "Logo Image");
        String coverPath = uploadImage(request.coverFile(), "Cover Image");

        Brand brand = new Brand(
                request.id(),
                request.themeColor(),
                request.nameEn(),
                request.nameKo(),
                request.description(),
                logoPath,
                coverPath
        );

        return brandRepository.save(brand);
    }

    @Transactional
    public Product createProduct(ProductCreateRequest request) {
        if (productRepository.existsById(request.id())) {
            throw new IllegalArgumentException("Product with ID '%s' already exists.".formatted(request.id()));
        }

        Brand brand = brandRepository.findById(request.brandId())
                .orElseThrow(() -> new IllegalArgumentException("Brand with ID '%s' not found.".formatted(request.brandId())));

        Product product = new Product(
                request.id(),
                brand,
                request.tier(),
                request.nameEn(),
                request.nameKo(),
                request.totalCount(),
                request.releaseDate(),
                request.isActive(),
                request.itemImageUrl()
        );

        Product savedProduct = productRepository.save(product);

        // Fetch max nftTokenId from items table for last_token_id
        int lastTokenId = itemRepository.findMaxNftTokenIdOrDefault();

        // Call AI server to generate token IDs
        List<Integer> tokenIds = aiServiceClient.generateTokenIds(
                savedProduct.getId(),
                savedProduct.getTotalCount(),
                lastTokenId
        );


        // Pre-create items in database with token_ids and null nfc_uid
        for (Integer tokenId : tokenIds) {
            Item item = new Item(
                    savedProduct,
                    null, // nfcUid is null initially
                    tokenId,
                    null,
                    "unclaimed"
            );
            itemRepository.save(item);
        }

        return savedProduct;
    }

    @Transactional
    public Item createItem(ItemRegisterRequest request) {
        if (itemRepository.existsByNfcUid(request.nfcUid())) {
            throw new IllegalArgumentException("Item with NFC UID '%s' already registered.".formatted(request.nfcUid()));
        }

        // Find the first unmapped item for this product
        Item item = itemRepository.findFirstByProductIdAndNfcUidIsNullOrderByNftTokenIdAsc(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("No available unclaimed items left to register NFC for product: " + request.productId()));

        // Map the NFC UID to this pre-created item
        item.setNfcUid(request.nfcUid());

        return itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public Page<Brand> getBrands(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> getProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    private String uploadImage(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 1. Size check (10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException(fieldName + " exceeds the maximum limit of 10MB.");
        }

        // 2. Format check
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException(fieldName + " has an invalid file name.");
        }
        int dotIdx = originalFilename.lastIndexOf(".");
        if (dotIdx == -1) {
            throw new IllegalArgumentException(fieldName + " has no file extension.");
        }
        String ext = originalFilename.substring(dotIdx + 1).toLowerCase();
        if (!List.of("png", "jpg", "jpeg", "gif", "webp").contains(ext)) {
            throw new IllegalArgumentException(fieldName + " has an unsupported file format. Allowed formats: PNG, JPG, GIF, WEBP.");
        }

        // 3. Save file
        try {
            File uploadDir = new File("uploads/brands").getAbsoluteFile();
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            String savedName = UUID.randomUUID().toString() + "." + ext;
            File destFile = new File(uploadDir, savedName);
            file.transferTo(destFile);
            return "/uploads/brands/" + savedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload " + fieldName, e);
        }
    }
}
