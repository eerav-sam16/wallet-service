package com.paytm.wallet.dto;

import com.paytm.wallet.model.TransferStatus;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class TransferResponse {
    private UUID transferId;
    private UUID from;
    private UUID to;
    private Long amountPaise;
    private TransferStatus status;
}
