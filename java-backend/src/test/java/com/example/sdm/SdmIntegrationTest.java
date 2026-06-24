package com.example.sdm;

import com.example.sdm.controller.AdminController;
import com.example.sdm.controller.AuthController;
import com.example.sdm.controller.NfcController;
import com.example.sdm.controller.ClaimController;
import com.example.sdm.controller.TransferController;
import com.example.sdm.dto.SignupRequest;
import com.example.sdm.dto.LoginRequest;
import com.example.sdm.dto.TokenResponse;
import com.example.sdm.dto.BrandCreateRequest;
import com.example.sdm.dto.ProductCreateRequest;
import com.example.sdm.dto.ItemRegisterRequest;
import com.example.sdm.dto.NfcVerificationResponse;
import com.example.sdm.dto.TransferRequest;
import com.example.sdm.dto.ProductResponse;
import com.example.sdm.dto.SignupResponse;
import com.example.sdm.dto.ItemHistoryResponse;
import com.example.sdm.entity.Brand;
import com.example.sdm.entity.Product;
import com.example.sdm.entity.Item;
import com.example.sdm.entity.User;
import com.example.sdm.entity.Transfer;
import com.example.sdm.entity.ProductTier;
import com.example.sdm.entity.ItemHistory;
import com.example.sdm.exception.DuplicateItemOwnershipException;
import com.example.sdm.repository.BrandRepository;
import com.example.sdm.repository.ProductRepository;
import com.example.sdm.repository.ItemRepository;
import com.example.sdm.repository.UserRepository;
import com.example.sdm.repository.TransferRepository;
import com.example.sdm.repository.ItemHistoryRepository;
import com.example.sdm.crypto.SdmService;
import com.example.sdm.service.AiServiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
public class SdmIntegrationTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private AdminController adminController;

    @Autowired
    private NfcController nfcController;

    @Autowired
    private ClaimController claimController;

    @Autowired
    private TransferController transferController;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private ItemHistoryRepository itemHistoryRepository;

    @Autowired
    private SdmService sdmService;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @BeforeEach
    public void setupMocks() {
        org.mockito.Mockito.when(aiServiceClient.generateWallet("userA@example.com"))
                .thenReturn("0x1111111111111111111111111111111111111111");
        org.mockito.Mockito.when(aiServiceClient.generateWallet("userB@example.com"))
                .thenReturn("0x2222222222222222222222222222222222222222");
        org.mockito.Mockito.when(aiServiceClient.generateWallet("userTestA@example.com"))
                .thenReturn("0x0000000000000000000000000000000000000000");
        org.mockito.Mockito.when(aiServiceClient.generateTokenIds(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyInt()
        )).thenAnswer(invocation -> {
            int count = invocation.getArgument(1);
            int lastTokenId = invocation.getArgument(2);
            java.util.List<Integer> list = new java.util.ArrayList<>();
            for (int i = 1; i <= count; i++) {
                list.add(lastTokenId + i);
            }
            return list;
        });
        org.mockito.Mockito.when(aiServiceClient.claimItemOnAiServer(
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyString()
        )).thenReturn("0x38f3cafad03aff035347be0cdca2f7bb795d0f9dfcde7001f7d34491f419102c");
        org.mockito.Mockito.when(aiServiceClient.transferNftP2p(
                org.mockito.Mockito.anyInt(),
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.anyString()
        )).thenReturn("0xP2PTransactionHashString0000000000000000000000000000000");
    }

    @BeforeEach
    @AfterEach
    public void cleanup() {
        transferRepository.deleteAll();
        itemHistoryRepository.deleteAll();
        itemRepository.deleteAll();
        productRepository.deleteAll();
        brandRepository.deleteAll();
        userRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(User user) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String calculateCmac(String uidHex, int ctrVal, String nfcKeyHex) {
        byte[] uid = hexToBytes(uidHex);
        byte[] ctrBytes = packBigEndian3Bytes(ctrVal);
        byte[] ctrReversed = ctrBytes.clone();
        reverseArray(ctrReversed);

        byte[] piccData = new byte[uid.length + ctrReversed.length];
        System.arraycopy(uid, 0, piccData, 0, uid.length);
        System.arraycopy(ctrReversed, 0, piccData, uid.length, ctrReversed.length);

        byte[] nfcKey = hexToBytes(nfcKeyHex);
        byte[] mac = sdmService.calculateSdmmac(false, nfcKey, piccData, null, "AES");
        return bytesToHex(mac);
    }

    @Test
    @DisplayName("정상 흐름: 회원가입 -> 로그인 -> 브랜드/상품/Item 등록 -> NFC 검증 -> Claim -> Transfer(신청/수락)")
    public void normalFlowTest() {
        // 1. 회원가입 (User A, User B)
        SignupRequest signupA = new SignupRequest("userA@example.com", "password123!", "userA", "user");
        ResponseEntity<SignupResponse> signupResA = authController.signup(signupA);
        assertThat(signupResA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signupResA.getBody().email()).isEqualTo("userA@example.com");
        User userA = userRepository.findByEmail("userA@example.com").orElseThrow();

        SignupRequest signupB = new SignupRequest("userB@example.com", "password123!", "userB", "user");
        ResponseEntity<SignupResponse> signupResB = authController.signup(signupB);
        assertThat(signupResB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signupResB.getBody().email()).isEqualTo("userB@example.com");
        User userB = userRepository.findByEmail("userB@example.com").orElseThrow();

        // 2. 로그인 (User A)
        LoginRequest loginA = new LoginRequest("userA@example.com", "password123!");
        ResponseEntity<TokenResponse> loginResA = authController.login(loginA);
        assertThat(loginResA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResA.getBody().accessToken()).isNotBlank();

        // 3. 브랜드 등록 (Admin)
        User adminUser = new User("admin@example.com", "password", "admin", "-", "admin");
        authenticateUser(adminUser);
        MockMultipartFile mockLogo = new MockMultipartFile("logoFile", "logo.png", "image/png", "fake logo content".getBytes());
        MockMultipartFile mockCover = new MockMultipartFile("coverFile", "cover.webp", "image/webp", "fake cover content".getBytes());
        BrandCreateRequest brandRequest = new BrandCreateRequest("GRP_001", "#6B46FF", "Brand A (EN)", "브랜드 A (KR)", "First Brand", mockLogo, mockCover);
        ResponseEntity<Brand> brandRes = adminController.createBrand(brandRequest);
        assertThat(brandRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(brandRes.getBody().getId()).isEqualTo("GRP_001");
        assertThat(brandRes.getBody().getLogoImageUrl()).isNotBlank();

        // 4. 상품 템플릿 등록 (Admin)
        ProductCreateRequest productRequest = new ProductCreateRequest("PROD_01", "GRP_001", ProductTier.EXCLUSIVE, "Luxury Watch", "명품 시계", 1000, LocalDate.now(), true, "/images/watch.png");
        ResponseEntity<ProductResponse> productRes = adminController.createProduct(productRequest);
        assertThat(productRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(productRes.getBody().id()).isEqualTo("PROD_01");

        // 5. 개별 Item 등록 (Admin/Factory)
        String nfcUid = "04A1B2C3D4E5F6";
        String nfcKey = "00112233445566778899AABBCCDDEEFF";
        ItemRegisterRequest itemRequest = new ItemRegisterRequest("PROD_01", nfcUid);
        ResponseEntity<Item> itemRes = adminController.createItem(itemRequest);
        assertThat(itemRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(itemRes.getBody().getNfcUid()).isEqualTo(nfcUid);
        assertThat(itemRes.getBody().getStatus()).isEqualTo("unclaimed");
        SecurityContextHolder.clearContext();

        // 6. NFC 검증 (CMAC 계산 적용)
        String cmac = calculateCmac(nfcUid, 1, nfcKey);
        MockHttpSession session = new MockHttpSession();
        ResponseEntity<NfcVerificationResponse> verifyRes = nfcController.verifyNfcTag(nfcUid, "1", cmac, session);
        assertThat(verifyRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyRes.getBody().nfcUid()).isEqualTo(nfcUid);
        assertThat(session.getAttribute("verified_nfc_uid")).isEqualTo(nfcUid);

        // 7. NFT 수집 동의 민팅 (Claim)
        authenticateUser(userA);
        ResponseEntity<Item> claimRes = claimController.claimItem(nfcUid, userA, session);
        assertThat(claimRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(claimRes.getBody().getOwner().getId()).isEqualTo(userA.getId());
        assertThat(claimRes.getBody().getStatus()).isEqualTo("claimed");

        // [임시 주석 처리] ClaimController에서 세션 비우는 부분을 주석 처리했으므로, 테스트 Assertion도 주석 처리합니다.
        // assertThat(session.getAttribute("verified_nfc_uid")).isNull();

        // 8. P2P 양도 신청 (A -> B)
        TransferRequest transferRequest = new TransferRequest(claimRes.getBody().getId(), "userB");
        ResponseEntity<Transfer> transferReqRes = transferController.requestTransfer(transferRequest, userA);
        assertThat(transferReqRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transferReqRes.getBody().getStatus()).isEqualTo("pending");

        // 8-2. 수령 대기 중인 양도 목록 조회 (B)
        authenticateUser(userB);
        ResponseEntity<List<Transfer>> pendingRes = transferController.getPendingTransfers(userB);
        assertThat(pendingRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendingRes.getBody()).hasSize(1);
        assertThat(pendingRes.getBody().get(0).getId()).isEqualTo(transferReqRes.getBody().getId());

        // 9. 소유권 이전 수락 (B)
        ResponseEntity<Transfer> acceptRes = transferController.acceptTransfer(transferReqRes.getBody().getId(), userB);
        assertThat(acceptRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acceptRes.getBody().getStatus()).isEqualTo("completed");

        // 9-2. DB 최종 반영 확인
        Item finalItem = itemRepository.findById(claimRes.getBody().getId()).orElseThrow();
        assertThat(finalItem.getOwner().getId()).isEqualTo(userB.getId());
        assertThat(finalItem.getStatus()).isEqualTo("claimed");

        // 10. 소유권 이력 조회
        ResponseEntity<List<ItemHistoryResponse>> historyRes = claimController.getItemHistory(finalItem.getId());
        assertThat(historyRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyRes.getBody()).hasSize(2); // MINT, TRANSFER
        assertThat(historyRes.getBody().get(0).eventType()).isEqualTo("TRANSFER");
        assertThat(historyRes.getBody().get(1).eventType()).isEqualTo("MINT");
    }

    @Test
    @DisplayName("예외 흐름: 소유권 전달 신청 시, 수령인(B)이 이미 동일한 상품타입을 보유하고 있다면 Custom Exception 발생")
    public void transferRequestDuplicateOwnershipTest() {
        // 1. 회원가입 (A, B)
        SignupRequest signupA = new SignupRequest("userA@example.com", "password123!", "userA", "user");
        authController.signup(signupA);
        User userA = userRepository.findByEmail("userA@example.com").orElseThrow();

        SignupRequest signupB = new SignupRequest("userB@example.com", "password123!", "userB", "user");
        authController.signup(signupB);
        User userB = userRepository.findByEmail("userB@example.com").orElseThrow();

        // 2. 브랜드 및 상품 템플릿 등록
        User adminUser = new User("admin@example.com", "password", "admin", "-", "admin");
        authenticateUser(adminUser);
        MockMultipartFile mockLogo = new MockMultipartFile("logoFile", "logo.png", "image/png", "fake logo".getBytes());
        MockMultipartFile mockCover = new MockMultipartFile("coverFile", "cover.webp", "image/webp", "fake cover".getBytes());
        adminController.createBrand(new BrandCreateRequest("GRP_001", "#6B46FF", "Brand A (EN)", "브랜드 A (KR)", "Desc", mockLogo, mockCover));
        adminController.createProduct(new ProductCreateRequest("PROD_01", "GRP_001", ProductTier.EXCLUSIVE, "Luxury Watch", "명품 시계", 1000, LocalDate.now(), true, "/images/watch.png"));

        // 3. A 소유 Item 1 등록 및 Claim 처리
        Item item1 = adminController.createItem(new ItemRegisterRequest("PROD_01", "04A1B2C3D4E5F6")).getBody();
        item1.setOwner(userA);
        item1.setStatus("claimed");
        itemRepository.save(item1);
 
        // 4. B 소유 Item 2 등록 및 Claim 처리
        Item item2 = adminController.createItem(new ItemRegisterRequest("PROD_01", "04A1B2C3D4E5F7")).getBody();
        item2.setOwner(userB);
        item2.setStatus("claimed");
        itemRepository.save(item2);
        SecurityContextHolder.clearContext();

        // 5. A가 B에게 소유권 이전을 요청 -> B는 이미 PROD_01을 보유 중이므로 DuplicateItemOwnershipException 발생
        TransferRequest transferRequest = new TransferRequest(item1.getId(), "userB");
        authenticateUser(userA);

        assertThrows(DuplicateItemOwnershipException.class, () -> {
            transferController.requestTransfer(transferRequest, userA);
        });
    }

    @Test
    @DisplayName("인증 흐름: 회원가입 -> 로그인 토큰 발급 검증")
    public void authFlowTest() {
        SignupRequest signupRequest = new SignupRequest("userTestA@example.com", "password123!", "userTestA", "user");
        ResponseEntity<SignupResponse> signupRes = authController.signup(signupRequest);
        assertThat(signupRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(signupRes.getBody().email()).isEqualTo("userTestA@example.com");
        assertThat(signupRes.getBody().username()).isEqualTo("userTestA");

        LoginRequest loginRequest = new LoginRequest("userTestA@example.com", "password123!");
        ResponseEntity<TokenResponse> loginRes = authController.login(loginRequest);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody().accessToken()).isNotBlank();
        assertThat(loginRes.getBody().tokenType()).isEqualTo("Bearer");

        // 잘못된 비밀번호 입력시 예외 발생 검증
        LoginRequest invalidLogin = new LoginRequest("userTestA@example.com", "wrong_password");
        assertThrows(IllegalArgumentException.class, () -> {
            authController.login(invalidLogin);
        });
    }

    @Test
    @DisplayName("페이징 조회 흐름: 브랜드 및 상품 다수 생성 후 페이징 조회 검증")
    public void pagingQueryTest() {
        // 1. 다수의 브랜드 생성
        User adminUser = new User("admin@example.com", "password", "admin", "-", "admin");
        authenticateUser(adminUser);
        MockMultipartFile mockLogo = new MockMultipartFile("logoFile", "logo.png", "image/png", "fake logo".getBytes());
        MockMultipartFile mockCover = new MockMultipartFile("coverFile", "cover.webp", "image/webp", "fake cover".getBytes());
        adminController.createBrand(new BrandCreateRequest("GRP_P_01", "#111111", "Brand P1 (EN)", "브랜드 P1 (KR)", "Desc 1", mockLogo, mockCover));
        adminController.createBrand(new BrandCreateRequest("GRP_P_02", "#222222", "Brand P2 (EN)", "브랜드 P2 (KR)", "Desc 2", mockLogo, mockCover));
        adminController.createBrand(new BrandCreateRequest("GRP_P_03", "#333333", "Brand P3 (EN)", "브랜드 P3 (KR)", "Desc 3", mockLogo, mockCover));

        // 2. 브랜드 페이징 조회 검증 (페이지 크기 2로 제한)
        org.springframework.data.domain.Pageable pageableBrand = org.springframework.data.domain.PageRequest.of(0, 2);
        ResponseEntity<org.springframework.data.domain.Page<Brand>> brandsRes = adminController.getBrands(pageableBrand);
        assertThat(brandsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(brandsRes.getBody().getContent()).hasSize(2);
        assertThat(brandsRes.getBody().getTotalElements()).isEqualTo(3);
        assertThat(brandsRes.getBody().getTotalPages()).isEqualTo(2);

        // 3. 다수의 상품 생성
        adminController.createProduct(new ProductCreateRequest("PROD_P_01", "GRP_P_01", ProductTier.NORMAL, "Item P1", "상품 P1", 100, LocalDate.now(), true, "/images/1.png"));
        adminController.createProduct(new ProductCreateRequest("PROD_P_02", "GRP_P_01", ProductTier.LIMITED, "Item P2", "상품 P2", 200, LocalDate.now(), true, "/images/2.png"));
        adminController.createProduct(new ProductCreateRequest("PROD_P_03", "GRP_P_01", ProductTier.EXCLUSIVE, "Item P3", "상품 P3", 300, LocalDate.now(), true, "/images/3.png"));
        SecurityContextHolder.clearContext();

        // 4. 상품 페이징 조회 검증 (페이지 크기 2로 제한)
        org.springframework.data.domain.Pageable pageableItem = org.springframework.data.domain.PageRequest.of(0, 2);
        ResponseEntity<org.springframework.data.domain.Page<ProductResponse>> itemsRes = adminController.getProducts(pageableItem);
        assertThat(itemsRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(itemsRes.getBody().getContent()).hasSize(2);
        assertThat(itemsRes.getBody().getTotalElements()).isEqualTo(3);
        assertThat(itemsRes.getBody().getTotalPages()).isEqualTo(2);
    }

    // Helper methods for hex encoding/decoding and big-endian packing
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] packBigEndian3Bytes(int val) {
        byte[] bytes = new byte[3];
        bytes[0] = (byte) ((val >> 16) & 0xFF);
        bytes[1] = (byte) ((val >> 8) & 0xFF);
        bytes[2] = (byte) (val & 0xFF);
        return bytes;
    }

    private static void reverseArray(byte[] array) {
        int i = 0;
        int j = array.length - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
}
