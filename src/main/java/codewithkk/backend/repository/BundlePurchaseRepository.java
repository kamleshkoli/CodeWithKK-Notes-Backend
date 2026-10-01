package codewithkk.backend.repository;

import codewithkk.backend.entity.BundlePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * All rows for a user. Preferred over findByUserId wherever a second match
     * is possible - that method throws IncorrectResultSize if a user somehow
     * has more than one purchase row.
     */
    @Query("select b from BundlePurchase b where b.userId = :userId order by b.purchaseDate desc")
    List<BundlePurchase> listByUserId(@Param("userId") String userId);

    List<BundlePurchase> findByStatus(String status);

    long countByStatus(String status);
}
