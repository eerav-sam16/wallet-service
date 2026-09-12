package com.paytm.wallet.service;

import com.paytm.wallet.dto.TransferRequest;
import com.paytm.wallet.dto.TransferResponse;
import com.paytm.wallet.exception.IdempotencyConflictException;
import com.paytm.wallet.exception.InsufficientFundsException;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.model.Transfer;
import com.paytm.wallet.model.TransferStatus;
import com.paytm.wallet.repository.TransferRepository;
import com.paytm.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final WalletRepository   walletRepository;
    private final MeterRegistry      meterRegistry;

    // TRANSFER — enforces all 3 gates in ONE @Transactional
    //
    // Gate 1 (idempotency):
    //   same key + same body  → 200 replay
    //   same key + diff body  → 409 conflict
    //   new key               → proceed
    //
    // Gate 2 (no overdraft):
    //   UPDATE WHERE balance >= amount
    //   0 rows = decline, 1 row = proceed
    //
    // Gate 3 (conservation):
    //   debit + credit in same transaction
    //   atomicity ensures money never lost
    @Transactional
    public TransferResponse transfer(TransferRequest request) {

        log.info("event=transfer_initiated from={} to={} amount_paise={} idempotency_key={}",
                request.getFrom(), request.getTo(),
                request.getAmountPaise(), request.getIdempotencyKey());

        // Step 1 — Idempotency check
        String requestHash = buildHash(request);
        Optional<Transfer> existing =
                transferRepository.findByIdempotencyKey(request.getIdempotencyKey());

        if (existing.isPresent()) {
            Transfer existingTransfer = existing.get();
            if (existingTransfer.getRequestHash().equals(requestHash)) {
                log.info("event=transfer_idempotent_replay transfer_id={} key={}",
                        existingTransfer.getId(), request.getIdempotencyKey());
                meterRegistry.counter("transfers.idempotent.replays").increment();
                return toResponse(existingTransfer);
            } else {
                log.warn("event=transfer_idempotency_conflict key={}", request.getIdempotencyKey());
                throw new IdempotencyConflictException(
                        "Idempotency key reused with different body: " + request.getIdempotencyKey());
            }
        }

        // Step 2 — Validate wallets exist
        if (!walletRepository.existsById(request.getFrom())) {
            throw new WalletNotFoundException("Source wallet not found: " + request.getFrom());
        }
        if (!walletRepository.existsById(request.getTo())) {
            throw new WalletNotFoundException("Destination wallet not found: " + request.getTo());
        }

        // Step 3 — Insert transfer record (anchors idempotency key in same tx)
        Transfer transfer = new Transfer();
        transfer.setIdempotencyKey(request.getIdempotencyKey());
        transfer.setFromWalletId(request.getFrom());
        transfer.setToWalletId(request.getTo());
        transfer.setAmountPaise(request.getAmountPaise());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setRequestHash(requestHash);
        transfer = transferRepository.save(transfer);

        // Step 4 — Conditional debit
        // ONE atomic SQL: UPDATE WHERE balance >= amount
        // 0 rows = insufficient funds, 1 row = success
        int rows = walletRepository.debit(request.getFrom(), request.getAmountPaise());

        if (rows == 0) {
            transfer.setStatus(TransferStatus.DECLINED);
            transferRepository.save(transfer);
            log.warn("event=transfer_declined reason=insufficient_funds transfer_id={} from={} amount={}",
                    transfer.getId(), request.getFrom(), request.getAmountPaise());
            meterRegistry.counter("transfers.declined.insufficient_funds").increment();
            throw new InsufficientFundsException(
                    "Insufficient funds in wallet: " + request.getFrom());
        }

        // Step 5 — Credit destination
        walletRepository.credit(request.getTo(), request.getAmountPaise());

        // Step 6 — Mark completed
        transfer.setStatus(TransferStatus.COMPLETED);
        transfer = transferRepository.save(transfer);

        log.info("event=transfer_completed transfer_id={} from={} to={} amount_paise={}",
                transfer.getId(), request.getFrom(), request.getTo(), request.getAmountPaise());
        meterRegistry.counter("transfers.created").increment();

        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(UUID transferId) {
        log.debug("event=transfer_get transfer_id={}", transferId);
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Transfer not found: " + transferId));
        return toResponse(transfer);
    }

    // MD5(from|to|amount) — detects same-key + different-body → 409
    private String buildHash(TransferRequest request) {
        try {
            String raw = request.getFrom() + "|" + request.getTo() + "|" + request.getAmountPaise();
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    private TransferResponse toResponse(Transfer transfer) {
        return TransferResponse.builder()
                .transferId(transfer.getId())
                .from(transfer.getFromWalletId())
                .to(transfer.getToWalletId())
                .amountPaise(transfer.getAmountPaise())
                .status(transfer.getStatus())
                .build();
    }
}
