package codewithkk.backend.repository;

import codewithkk.backend.entity.BundlePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BundlePurchaseRepository extends JpaRepository<BundlePurchase, String> {

    Optional<BundlePurchase> findByUserId(String userId);

    Optional<BundlePurchase> findByOrderId(String orderId);

    List<BundlePurchase> findAllByUserId(String userId);

    /**
     * Only completed rows count as access. Checking the status here keeps
     * "has this user paid or been granted" in one place, so a revoked or
     * failed row can never look like a valid purchase.
     */
    boolean existsByUserIdAndStatus(String userId, String status);

    boolean existsByUserId(String userId);

    long deleteByUserId(String userId);

    List<BundlePurchase> findByStatus(String status);

    long countByStatus(String status);
}
