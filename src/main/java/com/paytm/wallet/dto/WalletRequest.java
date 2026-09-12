package com.paytm.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WalletRequest {
    @NotBlank(message = "user_id is required")
    private String userId;
}
