package codewithkk.backend.service;

import codewithkk.backend.dto.StatsResponse;
import codewithkk.backend.dto.UserProfileResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.Note;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.BundlePurchaseRepository;
import codewithkk.backend.repository.NoteRepository;
import codewithkk.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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
