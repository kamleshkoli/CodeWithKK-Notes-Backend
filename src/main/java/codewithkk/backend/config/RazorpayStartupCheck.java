package codewithkk.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Verifies the Razorpay credentials at boot and logs the outcome.
 *
 * <p>Render masks secret environment variables, so a mis-pasted key secret cannot
 * be read back from the dashboard. Without this check the only symptom is a
 * 400 from {@code /api/payment/create-order} at the moment a customer tries to
 * buy something, which reports "authentication failed" and nothing more.
 *
 * <p>It issues an authenticated read against {@code GET /v1/payments} and never
 * creates an order, so it is side-effect free and safe to run on every deploy.
 */
@Component
public class RazorpayStartupCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RazorpayStartupCheck.class);

    private static final String PROBE_URL = "https://api.razorpay.com/v1/payments?count=1";

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Override
    public void run(String... args) {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            log.warn("Razorpay check SKIPPED - RAZORPAY_KEY_ID or RAZORPAY_KEY_SECRET is blank.");
            return;
        }

        // The key id is public (it is handed to the browser at checkout); the
        // secret is only ever reported by length so it cannot leak into logs.
        log.info("Razorpay check: probing with keyId='{}', secret {} chars",
                keyId, keySecret.length());

        String basicAuth = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(PROBE_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Basic " + basicAuth);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);

            int status = conn.getResponseCode();
            if (status == 200) {
                log.info("Razorpay check PASSED - credentials are valid. Payments should work.");
            } else {
                String body = readBody(conn.getErrorStream());
                log.error("Razorpay check FAILED - HTTP {} from Razorpay. Response: {}",
                        status, body);
                log.error("Razorpay check hint: HTTP 401 means the key id and secret do not "
                        + "belong to the same account. Check for a swapped pair, a stray "
                        + "leading/trailing space, or a truncated paste.");
            }
        } catch (Exception e) {
            log.error("Razorpay check could not reach Razorpay ({}: {}). "
                    + "Checkout will fail until this resolves.", e.getClass().getSimpleName(), e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readBody(InputStream in) {
        if (in == null) {
            return "(no body)";
        }
        try (InputStream stream = in) {
            byte[] bytes = stream.readAllBytes();
            String body = new String(bytes, StandardCharsets.UTF_8);
            // Razorpay error bodies are small JSON; cap it so a huge response
            // cannot flood the log.
            return body.length() > 500 ? body.substring(0, 500) + "..." : body;
        } catch (Exception e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }
}
