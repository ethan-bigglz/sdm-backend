package com.example.sdm.service;

import com.example.sdm.dto.TransferRequest;
import com.example.sdm.entity.Item;
import com.example.sdm.entity.ItemHistory;
import com.example.sdm.entity.Transfer;
import com.example.sdm.entity.User;
import com.example.sdm.exception.DuplicateItemOwnershipException;
import com.example.sdm.repository.ItemHistoryRepository;
import com.example.sdm.repository.ItemRepository;
import com.example.sdm.repository.TransferRepository;
import com.example.sdm.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final ItemRepository itemRepository;
    private final TransferRepository transferRepository;
    private final UserRepository userRepository;
    private final ItemHistoryRepository itemHistoryRepository;
    private final AiServiceClient aiServiceClient;

    public TransferService(ItemRepository itemRepository, 
                           TransferRepository transferRepository, 
                           UserRepository userRepository,
                           ItemHistoryRepository itemHistoryRepository,
                           AiServiceClient aiServiceClient) {
        this.itemRepository = itemRepository;
        this.transferRepository = transferRepository;
        this.userRepository = userRepository;
        this.itemHistoryRepository = itemHistoryRepository;
        this.aiServiceClient = aiServiceClient;
    }

    @Transactional
    public Transfer requestTransfer(TransferRequest request, User sender) {
        Item item = itemRepository.findById(request.itemId())
                .orElseThrow(() -> new IllegalArgumentException("Item with ID %d not found.".formatted(request.itemId())));

        // Validate current owner
        if (item.getOwner() == null || !item.getOwner().getId().equals(sender.getId())) {
            throw new IllegalArgumentException("You do not own this item. Ownership verification failed.");
        }

        // Validate item status is claimed
        if (!"claimed".equals(item.getStatus())) {
            throw new IllegalStateException("Item is not in claimed status. Current status: " + item.getStatus());
        }

        // Find recipient user
        User recipient = userRepository.findByUsername(request.recipientUsername())
                .orElseThrow(() -> new IllegalArgumentException("Recipient username '%s' not found.".formatted(request.recipientUsername())));

        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Sender and recipient cannot be the same user.");
        }

        // Check if recipient already owns an item of the same product template
        if (itemRepository.existsByOwnerIdAndProductId(recipient.getId(), item.getProduct().getId())) {
            throw new DuplicateItemOwnershipException("Recipient already owns an item of this product type.");
        }

        // Update item status
        item.setStatus("pending_transfer");
        itemRepository.save(item);

        // Create and save transfer request
        Transfer transfer = new Transfer(item, sender, recipient, "pending");
        return transferRepository.save(transfer);
    }

    @Transactional(readOnly = true)
    public List<Transfer> getPendingTransfersForRecipient(User recipient) {
        // Find pending transfers where recipient matches
        return transferRepository.findByRecipientId(recipient.getId()).stream()
                .filter(t -> "pending".equals(t.getStatus()))
                .toList();
    }

    @Transactional
    public Transfer acceptTransfer(Integer id, User recipient) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request with ID %d not found.".formatted(id)));

        if (!"pending".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer is not in pending status. Current status: " + transfer.getStatus());
        }

        if (!transfer.getRecipient().getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("You are not authorized to accept this transfer.");
        }

        // Validate wallet addresses
        String fromWallet = transfer.getSender().getWalletAddress();
        String toWallet = recipient.getWalletAddress();
        if (fromWallet == null || fromWallet.isBlank() || toWallet == null || toWallet.isBlank()) {
            throw new IllegalStateException("Wallet addresses for sender and/or recipient are missing.");
        }

        Item item = transfer.getItem();

        // Call AI server to execute the P2P transfer on the blockchain
        String txHash = aiServiceClient.transferNftP2p(item.getNftTokenId(), fromWallet, toWallet);

        // Update transfer status
        transfer.setStatus("completed");
        Transfer savedTransfer = transferRepository.save(transfer);

        // Update item owner and status
        item.setOwner(recipient);
        item.setStatus("claimed");
        itemRepository.save(item);

        // Log transfer history with the actual txHash
        ItemHistory history = new ItemHistory(item, "TRANSFER", transfer.getSender(), recipient, txHash);
        itemHistoryRepository.save(history);

        return savedTransfer;
    }

    @Transactional
    public Transfer rejectTransfer(Integer id, User recipient) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request with ID %d not found.".formatted(id)));

        if (!"pending".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer is not in pending status. Current status: " + transfer.getStatus());
        }

        if (!transfer.getRecipient().getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("You are not authorized to reject this transfer.");
        }

        // Update transfer status
        transfer.setStatus("rejected");
        Transfer savedTransfer = transferRepository.save(transfer);

        // Restore item status to claimed
        Item item = transfer.getItem();
        item.setStatus("claimed");
        itemRepository.save(item);

        return savedTransfer;
    }

    @Transactional
    public Transfer cancelTransfer(Integer id, User sender) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transfer request with ID %d not found.".formatted(id)));

        if (!"pending".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer is not in pending status. Current status: " + transfer.getStatus());
        }

        if (!transfer.getSender().getId().equals(sender.getId())) {
            throw new IllegalArgumentException("You are not authorized to cancel this transfer.");
        }

        // Update transfer status
        transfer.setStatus("cancelled");
        Transfer savedTransfer = transferRepository.save(transfer);

        // Restore item status to claimed
        Item item = transfer.getItem();
        item.setStatus("claimed");
        itemRepository.save(item);

        return savedTransfer;
    }
}
