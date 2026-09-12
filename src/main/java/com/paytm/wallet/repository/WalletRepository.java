package com.paytm.wallet.repository;

import com.paytm.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(String userId);

    // Race-free get-or-create (Gate 1)
    // ON CONFLICT DO NOTHING on UNIQUE user_id
    // guarantees exactly one wallet even under 50 concurrent requests
    @Modifying
    @Query(value = """
            INSERT INTO wallets (id, user_id, balance_paise, created_at, updated_at)
            VALUES (gen_random_uuid(), :userId, 0, NOW(), NOW())
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    void upsertWallet(@Param("userId") String userId);

    // Conditional debit (Gate 3)
    // CHECK + DEBIT in ONE atomic SQL statement
    // Returns 1 = success, 0 = insufficient funds
    @Modifying
    @Query("""
            UPDATE Wallet w
            SET w.balancePaise = w.balancePaise - :amount,
                w.updatedAt    = CURRENT_TIMESTAMP
            WHERE w.id           = :walletId
            AND   w.balancePaise >= :amount
            """)
    int debit(@Param("walletId") UUID walletId, @Param("amount") long amount);

    // Credit — always succeeds
    @Modifying
    @Query("""
            UPDATE Wallet w
            SET w.balancePaise = w.balancePaise + :amount,
                w.updatedAt    = CURRENT_TIMESTAMP
            WHERE w.id = :walletId
            """)
    void credit(@Param("walletId") UUID walletId, @Param("amount") long amount);
}
