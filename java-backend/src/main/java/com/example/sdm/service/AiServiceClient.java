package com.example.sdm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(@Value("${app.ai-server.url:http://localhost:5000}") String aiServerUrl) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();

        this.restClient = RestClient.builder()
                .baseUrl(aiServerUrl)
                .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * Calls AI server's wallet generation API.
     * Request: POST /api/v1/ai/wallet/create
     * Request Body: { "user_id": "email" }
     * Response: { "wallet_address": "0x..." }
     */
    public String generateWallet(String email) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/ai/wallet/create")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("user_id", email))
                    .retrieve()
                    .body(Map.class);
 
            if (response == null || !response.containsKey("wallet_address")) {
                throw new IllegalStateException("Failed to generate wallet: Empty or invalid response from AI server.");
            }
 
            return (String) response.get("wallet_address");
        } catch (Exception e) {
            throw new IllegalStateException("Error communicating with AI server: " + e.getMessage(), e);
        }
    }
 
    /**
     * Calls AI server's token generation API.
     * Request: POST /api/v1/ai/tokens/generate
     * Request Body: { "product_id": productId, "count": count, "last_token_id": lastTokenId }
     * Response: { "status": "success", "token_ids": [0, 1, 2, ...] }
     */
    public List<Integer> generateTokenIds(String productId, int count, int lastTokenId) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/ai/tokens/generate")
                    .header("Accept", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "product_id", productId,
                            "count", count,
                            "last_token_id", lastTokenId
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"success".equals(response.get("status")) || !response.containsKey("token_ids")) {
                throw new IllegalStateException("Failed to generate tokens: Empty or invalid response from AI server.");
            }

            @SuppressWarnings("unchecked")
            List<?> tokenIdsObj = (List<?>) response.get("token_ids");
            if (tokenIdsObj == null) {
                throw new IllegalStateException("Token IDs from AI server is null.");
            }

            return tokenIdsObj.stream()
                    .map(obj -> {
                        if (obj instanceof Number) {
                            return ((Number) obj).intValue();
                        } else {
                            return Integer.parseInt(obj.toString());
                        }
                    })
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Error communicating with AI server: " + e.getMessage(), e);
        }
    }

    /**
     * Calls AI server's item claim API to transfer tokens from admin to user.
     * Request: POST /api/v1/ai/nft/transfer-claim
     * Request Body: { "token_id": tokenId, "to_user_id": toUserId }
     * Response: { "status": "success", "tx_hash": "..." }
     */
    public String claimItemOnAiServer(Integer tokenId, String toUserId) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/ai/nft/transfer-claim")
                    .header("Accept", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "token_id", tokenId,
                            "to_user_id", toUserId
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"success".equals(response.get("status")) || !response.containsKey("tx_hash")) {
                throw new IllegalStateException("Failed to claim item on AI server: Empty or invalid response.");
            }

            return (String) response.get("tx_hash");
        } catch (Exception e) {
            throw new IllegalStateException("Error communicating with AI server during claim: " + e.getMessage(), e);
        }
    }

    /**
     * Calls AI server's P2P token transfer API to transfer NFT between users.
     * Request: POST /api/v1/ai/nft/transfer-p2p
     * Request Body: { "token_id": tokenId, "from_wallet_address": fromWalletAddress, "to_wallet_address": toWalletAddress }
     * Response: { "status": "success", "tx_hash": "..." }
     */
    public String transferNftP2p(Integer tokenId, String fromWalletAddress, String toWalletAddress) {
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/v1/ai/nft/transfer-p2p")
                    .header("Accept", "application/json")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "token_id", tokenId,
                            "from_wallet_address", fromWalletAddress,
                            "to_wallet_address", toWalletAddress
                    ))
                    .retrieve()
                    .body(Map.class);

            if (response == null || !"success".equals(response.get("status")) || !response.containsKey("tx_hash")) {
                throw new IllegalStateException("Failed to transfer NFT P2P on AI server: Empty or invalid response.");
            }

            return (String) response.get("tx_hash");
        } catch (Exception e) {
            throw new IllegalStateException("Error communicating with AI server during P2P transfer: " + e.getMessage(), e);
        }
    }

    private record AiWalletCreateRequest(String user_id) {}
}
