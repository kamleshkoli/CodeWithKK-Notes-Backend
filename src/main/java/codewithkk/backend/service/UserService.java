package codewithkk.backend.service;

import codewithkk.backend.dto.AdminSubscriptionResponse;
import codewithkk.backend.dto.StatsResponse;
import codewithkk.backend.dto.UserProfileResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.Note;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.BundlePurchaseRepository;
import codewithkk.backend.repository.NoteRepository;
import codewithkk.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private BundlePurchaseRepository bundlePurchaseRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    public UserProfileResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean hasPurchased = bundlePurchaseRepository.existsByUserId(userId);
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.getCreatedAt(), hasPurchased);
    }

    public UserProfileResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        boolean hasPurchased = bundlePurchaseRepository.existsByUserId(user.getId());
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.getCreatedAt(), hasPurchased);
    }

    /**
     * Self-service profile update. Deliberately ignores the incoming role, email
     * and id so a user cannot promote themselves to ADMIN by posting
     * {"role":"ROLE_ADMIN"} to a public endpoint.
     */
    public User updateOwnProfile(String userId, String email, User submitted) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (submitted.getName() != null) user.setName(submitted.getName());
        if (submitted.getPassword() != null && !submitted.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(submitted.getPassword()));
        }
        return userRepository.save(user);
    }

    /** Admin-only update. This is the one path allowed to change role. */
    public User updateUser(String userId, User updated) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (updated.getName() != null) user.setName(updated.getName());
        if (updated.getEmail() != null) user.setEmail(updated.getEmail());
        if (updated.getPassword() != null && !updated.getPassword().isBlank())
            user.setPassword(passwordEncoder.encode(updated.getPassword()));
        if (updated.getRole() != null) user.setRole(updated.getRole());
        return userRepository.save(user);
    }

    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<Note> getAllNotes() {
        return noteRepository.findAll();
    }

    public List<Note> getActiveNotes() {
        return noteRepository.findByActiveTrue();
    }

    public List<BundlePurchase> getAllPurchases() {
        return bundlePurchaseRepository.findAll();
    }

    /**
     * The updated Subscriptions row for one email, so the grant response carries
     * the same shape the list endpoint returns. Never null: grantAccess has
     * already proved the user exists.
     */
    public ResponseEntity<AdminSubscriptionResponse> findSubscriptionByEmail(String email) {
        User user = userRepository.findByEmail(email == null ? "" : email.trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return ResponseEntity.ok(new AdminSubscriptionResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                bundlePurchaseRepository.existsByUserIdAndStatus(user.getId(), "completed")));
    }

    /**
     * Every user with their current access state, for the admin Subscriptions tab.
     */
    public List<AdminSubscriptionResponse> getSubscriptions() {
        return userRepository.findAll().stream()
                .map(user -> new AdminSubscriptionResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole(),
                        bundlePurchaseRepository.existsByUserIdAndStatus(user.getId(), "completed")))
                .toList();
    }

    /**
     * Grants premium access by writing the same completed purchase record that a
     * real payment produces, so every existing access check keeps working
     * unchanged - the download gate and /api/bundle/me both already treat a
     * completed row as "has access".
     *
     * <p>Idempotent: granting twice returns the existing record instead of
     * creating a duplicate.
     */
    @Transactional
    public BundlePurchase grantAccess(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("No user found with email " + email));

        Optional<BundlePurchase> existing = bundlePurchaseRepository.findByUserId(user.getId());
        if (existing.isPresent()) {
            BundlePurchase purchase = existing.get();
            // Re-granting a revoked user restores them; re-granting an active
            // user is a no-op rather than an error.
            if (!"completed".equals(purchase.getStatus())) {
                purchase.setStatus("completed");
                if (purchase.getPurchaseDate() == null) {
                    purchase.setPurchaseDate(LocalDateTime.now());
                }
                return bundlePurchaseRepository.save(purchase);
            }
            return purchase;
        }

        BundlePurchase purchase = new BundlePurchase();
        purchase.setUserId(user.getId());
        purchase.setAmount(0.0);
        purchase.setPurchaseDate(LocalDateTime.now());
        purchase.setStatus("completed");
        // Marks the row as manually granted rather than paid, so the admin
        // payments list does not imply revenue that was never collected.
        purchase.setPaymentId("manual_grant");
        purchase.setOrderId("manual_" + System.currentTimeMillis());
        return bundlePurchaseRepository.save(purchase);
    }

    /** Removes access. Admins always retain access via their role, not a row. */
    @Transactional
    public void revokeAccess(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User id is required");
        }
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found");
        }
        bundlePurchaseRepository.deleteByUserId(userId);
    }

    public StatsResponse getStats() {
        long totalUsers = userRepository.count();
        long totalNotes = noteRepository.count();
        long activeNotes = noteRepository.countByActiveTrue();
        long totalPurchases = bundlePurchaseRepository.countByStatus("completed");
        List<BundlePurchase> allPurchases = bundlePurchaseRepository.findByStatus("completed");
        double totalRevenue = allPurchases.stream().mapToDouble(BundlePurchase::getAmount).sum();
        return new StatsResponse(totalUsers, totalNotes, totalPurchases, totalRevenue, activeNotes);
    }
}
