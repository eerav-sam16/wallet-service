package com.paytm.wallet.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {
    private UUID walletId;
    private String userId;
    private Long balancePaise;
}
