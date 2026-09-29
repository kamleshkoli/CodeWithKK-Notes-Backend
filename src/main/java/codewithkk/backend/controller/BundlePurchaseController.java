package codewithkk.backend.controller;

import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.UserRepository;
import codewithkk.backend.service.BundlePurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only. There is deliberately no public "create purchase" endpoint -
 * access is recorded solely by PaymentService after Razorpay confirms payment.
 */
@RestController
@RequestMapping("/api/bundle")
@CrossOrigin(origins = "*")
public class BundlePurchaseController {

    @Autowired
    private BundlePurchaseService bundlePurchaseService;

    @Autowired
    private UserRepository userRepository;

    /** Only the caller's own purchase record. */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getPurchase(@PathVariable String userId) {
        String callerId = currentUserId();
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!callerId.equals(userId) && !isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(bundlePurchaseService.getPurchaseByUserId(userId));
    }

    @GetMapping("/check/{userId}")
    public ResponseEntity<Boolean> hasPurchased(@PathVariable String userId) {
        String callerId = currentUserId();
        if (callerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!callerId.equals(userId) && !isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(bundlePurchaseService.hasPurchased(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myPurchase() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(401).build();
        }
        Optional<User> userOpt = userRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        Optional<BundlePurchase> purchase =
                bundlePurchaseService.getPurchaseByUserId(userOpt.get().getId());
        Map<String, Object> body = new HashMap<>();
        body.put("purchased", purchase.isPresent());
        body.put("purchase", purchase.orElse(null));
        return ResponseEntity.ok(body);
    }

    private String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .map(User::getId)
                .orElse(null);
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
