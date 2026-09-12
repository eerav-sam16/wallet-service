package com.paytm.wallet.service;

import com.paytm.wallet.dto.WalletRequest;
import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.exception.WalletNotFoundException;
import com.paytm.wallet.model.Wallet;
import com.paytm.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    // GET OR CREATE — race-free (Gate 1)
    // ON CONFLICT DO NOTHING ensures only 1 wallet
    // even under 50 concurrent requests for same user
    @Transactional
    public WalletResponse getOrCreateWallet(WalletRequest request) {
        log.info("event=wallet_get_or_create user_id={}", request.getUserId());

        walletRepository.upsertWallet(request.getUserId());

        Wallet wallet = walletRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + request.getUserId()));

        log.info("event=wallet_returned wallet_id={} user_id={} balance_paise={}",
                wallet.getId(), wallet.getUserId(), wallet.getBalancePaise());

        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        log.debug("event=wallet_get wallet_id={}", walletId);
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found: " + walletId));
        return toResponse(wallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .balancePaise(wallet.getBalancePaise())
                .build();
    }
}
