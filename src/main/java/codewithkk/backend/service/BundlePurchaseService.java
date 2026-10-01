package codewithkk.backend.service;

import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.repository.BundlePurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class BundlePurchaseService {

    @Autowired
    private BundlePurchaseRepository bundlePurchaseRepository;

    public BundlePurchase savePurchase(BundlePurchase purchase) {
        return bundlePurchaseRepository.save(purchase);
    }

    public Optional<BundlePurchase> getPurchaseByUserId(String userId) {
        List<BundlePurchase> purchases = bundlePurchaseRepository.findAllByUserId(userId);
        return purchases.stream()
                .filter(p -> p.getPurchaseDate() != null)
                .max(Comparator.comparing(BundlePurchase::getPurchaseDate))
                .or(() -> purchases.stream().findFirst());
    }

    /**
     * Only a completed row counts. A pending or failed payment must never look
     * like access, so the status is part of the check rather than implied.
     */
    public boolean hasPurchased(String userId) {
        return bundlePurchaseRepository.existsByUserIdAndStatus(userId, "completed");
    }
}