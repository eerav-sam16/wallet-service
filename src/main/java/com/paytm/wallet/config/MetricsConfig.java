package com.paytm.wallet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void initCounters() {
        Counter.builder("transfers.created")
                .description("Total transfers successfully completed")
                .register(meterRegistry);
        Counter.builder("transfers.declined.insufficient_funds")
                .description("Transfers declined due to insufficient funds")
                .register(meterRegistry);
        Counter.builder("transfers.idempotent.replays")
                .description("Idempotent transfer replays served")
                .register(meterRegistry);
    }
}
