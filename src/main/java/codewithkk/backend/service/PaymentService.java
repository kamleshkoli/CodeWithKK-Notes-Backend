package codewithkk.backend.service;

import codewithkk.backend.config.RazorpayConfig;
import codewithkk.backend.dto.PaymentOrderResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.BundlePurchaseRepository;
import codewithkk.backend.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private RazorpayConfig razorpayConfig;

    @Autowired
    private BundlePurchaseRepository bundlePurchaseRepository;

    @Autowired
    private UserRepository userRepository;

    public PaymentOrderResponse createOrder(double amount, String currency) {
        if (!razorpayConfig.isConfigured()) {
            throw new IllegalStateException("Razorpay is not configured on this server");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) Math.round(amount * 100));
            orderRequest.put("currency", currency != null ? currency : "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);
            return new PaymentOrderResponse(
                    order.get("id"),
                    amount,
                    currency != null ? currency : "INR",
                    razorpayConfig.getKeyId()
            );
        } catch (RazorpayException e) {
            // Surface Razorpay's own description. Without it every credential or
            // network problem collapses into one opaque "failed" and the real
            // cause has to be guessed at from a log the user cannot see.
            // keyId is logged (it is public); the secret is never logged.
            log.error("Razorpay order creation failed. keyId={} amount={} currency={} reason={}",
                    razorpayConfig.getKeyId(), amount, currency, e.getMessage(), e);
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    /**
     * Confirms a payment with Razorpay before recording access.
     *
     * Three independent checks, all required:
     *   1. The HMAC signature matches, computed with the key SECRET.
     *   2. Razorpay reports the order as actually paid.
     *   3. The buyer is the authenticated caller, taken from the JWT.
     *
     * The amount recorded is the one Razorpay reports, never a client-supplied
     * value, so a tampered request body cannot change what was paid.
     */
    @Transactional
    public BundlePurchase verifyPayment(String razorpayOrderId, String razorpayPaymentId,
                                        String razorpaySignature, String authenticatedEmail) {
        if (razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null
                || razorpayOrderId.isBlank() || razorpayPaymentId.isBlank() || razorpaySignature.isBlank()) {
            throw new IllegalArgumentException("Missing payment verification details");
        }

        // 1. Signature must match, using the key secret.
        String expected = hmacSha256(razorpayOrderId + "|" + razorpayPaymentId,
                razorpayConfig.getKeySecret());
        if (!constantTimeEquals(expected, razorpaySignature)) {
            throw new SecurityException("Payment signature verification failed");
        }

        // 2. Razorpay must confirm the order was genuinely paid.
        double paidRupees;
        try {
            Order order = razorpayClient.orders.fetch(razorpayOrderId);
            String status = (String) order.get("status");
            if (!"paid".equalsIgnoreCase(status)) {
                throw new SecurityException("Order is not paid (status: " + status + ")");
            }
            // amount_paid is in paise; fall back to amount if absent.
            Number amountPaid = (Number) order.get("amount_paid");
            if (amountPaid == null) {
                throw new SecurityException("Razorpay order has no paid amount");
            }
            paidRupees = amountPaid.longValue() / 100.0;
        } catch (RazorpayException e) {
            throw new RuntimeException("Could not confirm payment with Razorpay", e);
        }

        // 3. Bind the purchase to the authenticated user, not a body field.
        Optional<User> buyer = userRepository.findByEmail(authenticatedEmail);
        if (buyer.isEmpty()) {
            throw new SecurityException("Authenticated user not found");
        }

        // Replay guard: one purchase per order.
        if (bundlePurchaseRepository.findByOrderId(razorpayOrderId).isPresent()) {
            throw new IllegalStateException("This order has already been recorded");
        }

        BundlePurchase purchase = new BundlePurchase();
        purchase.setUserId(buyer.get().getId());
        purchase.setPaymentId(razorpayPaymentId);
        purchase.setOrderId(razorpayOrderId);
        purchase.setAmount(paidRupees);
        purchase.setPurchaseDate(LocalDateTime.now());
        purchase.setStatus("completed");

        return bundlePurchaseRepository.save(purchase);
    }

    private static String hmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /** Compares without leaking timing information. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
