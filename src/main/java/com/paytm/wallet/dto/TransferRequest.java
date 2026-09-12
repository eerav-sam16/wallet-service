package com.paytm.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class TransferRequest {
    @NotNull(message = "from wallet id is required")
    private UUID from;

    @NotNull(message = "to wallet id is required")
    private UUID to;

    @NotNull(message = "amount_paise is required")
    @Min(value = 1, message = "amount_paise must be at least 1")
    private Long amountPaise;

    @NotBlank(message = "idempotency_key is required")
    private String idempotencyKey;
}
