package codewithkk.backend.controller;

import codewithkk.backend.dto.PaymentOrderResponse;
import codewithkk.backend.dto.VerifyPaymentRequest;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponse> createOrder(@RequestBody Map<String, Object> body) {
        double amount = Double.parseDouble(body.getOrDefault("amount", 22).toString());
        String currency = body.getOrDefault("currency", "INR").toString();
        return ResponseEntity.ok(paymentService.createOrder(amount, currency));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody VerifyPaymentRequest request) {
        // The buyer comes from the verified JWT, never from the request body.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        BundlePurchase purchase = paymentService.verifyPayment(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                auth.getName()
        );
        return ResponseEntity.ok(purchase);
    }
}
