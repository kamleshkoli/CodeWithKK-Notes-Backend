package codewithkk.backend.repository;

import codewithkk.backend.entity.BundlePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BundlePurchaseRepository extends JpaRepository<BundlePurchase, String> {

    Optional<BundlePurchase> findByUserId(String userId);

    Optional<BundlePurchase> findByOrderId(String orderId);

    List<BundlePurchase> findAllByUserId(String userId);

    boolean existsByUserId(String userId);

    List<BundlePurchase> findByStatus(String status);

    long countByStatus(String status);
}
