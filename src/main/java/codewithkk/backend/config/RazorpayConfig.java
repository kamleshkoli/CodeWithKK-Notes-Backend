package codewithkk.backend.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class RazorpayConfig {

    private static final Logger log = LoggerFactory.getLogger(RazorpayConfig.class);

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    /**
     * Logs which credential the process actually loaded. The key id is public by
     * design (the browser receives it too); the secret is only ever logged as a
     * length, so this stays safe while making a mis-pasted key obvious.
     */
    @PostConstruct
    public void logLoadedCredentials() {
        if (isConfigured()) {
            log.info("Razorpay configured. keyId='{}' ({} chars), secret loaded ({} chars).",
                    keyId, keyId.length(), keySecret.length());
        } else {
            log.warn("Razorpay is NOT configured - key id or secret is blank. "
                    + "Payments will fail until RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are set.");
        }
    }

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        return new RazorpayClient(keyId, keySecret);
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }
}
