package com.example.sdm.controller;

import com.example.sdm.dto.TransferRequest;
import com.example.sdm.entity.Transfer;
import com.example.sdm.entity.User;
import com.example.sdm.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "5. P2P Transfer API", description = "유저 간 디지털 소유권(NFT) 양도 프로세스")
@RestController
@RequestMapping("/api/v1/transfers")
@PreAuthorize("hasRole('USER')")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @Operation(summary = "[5-1] 소유권 이전 전송 요청", description = "현재 소유자가 특정 수령인을 지정해 이전 신청(pending 상태)을 작성합니다.")
    @PostMapping("/request")
    public ResponseEntity<Transfer> requestTransfer(
        @RequestBody TransferRequest request,
        @AuthenticationPrincipal User sender
    ) {
        Transfer transfer = transferService.requestTransfer(request, sender);
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
    }

    @Operation(summary = "[5-2] 수령 대기 중인 양도 목록 조회", description = "로그인한 사용자가 수령 수락해야 할 대기 상태의 양도 이력 목록을 조회합니다.")
    @GetMapping("/pending")
    public ResponseEntity<List<Transfer>> getPendingTransfers(@AuthenticationPrincipal User recipient) {
        List<Transfer> list = transferService.getPendingTransfersForRecipient(recipient);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "[5-3] 소유권 이전 전송 수락", description = "수령인이 양도를 수락하여 최종적으로 실물 및 NFT 소유권을 이전합니다.")
    @PostMapping("/{id}/accept")
    public ResponseEntity<Transfer> acceptTransfer(
        @PathVariable("id") Integer id,
        @AuthenticationPrincipal User recipient
    ) {
        Transfer transfer = transferService.acceptTransfer(id, recipient);
        return ResponseEntity.ok(transfer);
    }

    @Operation(summary = "[5-4] 소유권 이전 전송 거절", description = "수령인이 전송 요청을 거절하여 상태를 복원합니다.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Transfer> rejectTransfer(
        @PathVariable("id") Integer id,
        @AuthenticationPrincipal User recipient
    ) {
        Transfer transfer = transferService.rejectTransfer(id, recipient);
        return ResponseEntity.ok(transfer);
    }

    @Operation(summary = "[5-5] 소유권 이전 전송 취소", description = "요청한 송신자가 전송 요청을 취소하여 상태를 복원합니다.")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Transfer> cancelTransfer(
        @PathVariable("id") Integer id,
        @AuthenticationPrincipal User sender
    ) {
        Transfer transfer = transferService.cancelTransfer(id, sender);
        return ResponseEntity.ok(transfer);
    }
}
