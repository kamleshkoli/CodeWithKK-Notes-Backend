package codewithkk.backend.dto;

import lombok.Data;

/**
 * Only the three values Razorpay signs. The buyer is derived from the JWT and
 * the amount is read back from Razorpay, so neither is accepted from the client.
 */
@Data
public class VerifyPaymentRequest {
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;
}
