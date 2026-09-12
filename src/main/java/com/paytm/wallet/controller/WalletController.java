package com.paytm.wallet.controller;

import com.paytm.wallet.dto.WalletRequest;
import com.paytm.wallet.dto.WalletResponse;
import com.paytm.wallet.service.WalletService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/")
    public void root(HttpServletResponse response) throws IOException {
        response.sendRedirect("/swagger-ui/index.html");
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createOrGetWallet(
            @Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.getOrCreateWallet(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(@PathVariable UUID id) {
        return ResponseEntity.ok(walletService.getWallet(id));
    }
}
