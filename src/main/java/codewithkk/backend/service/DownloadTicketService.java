package codewithkk.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Short-lived, signed tickets that let a browser <em>navigate</em> to a
 * protected PDF.
 *
 * <p>Why this exists: the download endpoint is protected by a bearer JWT, and a
 * browser navigation cannot attach an Authorization header. The old workaround
 * was to fetch the PDF with axios and hand the browser a blob: URL. That works
 * on desktop, but Android WebViews (Instagram, Facebook, many in-app browsers)
 * cannot render PDF at all, and the blob path discards the server's
 * Content-Disposition header, so the browser is never told to save the file. A
 * real navigation is the only request shape those browsers handle.
 *
 * <p>This is NOT a weakening of the access check. A ticket is only ever minted
 * after the caller has passed the same JWT + completed-purchase gate the
 * endpoint already enforced, and {@link #verify} re-checks the purchase in the
 * controller before streaming anything, so revoking access invalidates
 * outstanding tickets immediately.
 *
 * <p>Design note - why signed instead of a stored single-use token:
 * an earlier version kept tickets in an in-memory map and burned them on first
 * read. That 403'd real buyers on mobile, because a tap can produce more than
 * one request to the URL (browser prefetch, a retried navigation) and because
 * backgrounded tabs can delay the final navigation past a short expiry. It also
 * broke outright if the app ran more than one instance, since the ticket was
 * minted on one instance and consumed on another. A signed, stateless token is
 * bound to a user and a note and carries its own expiry, so it survives all
 * three without any shared state - an old or leaked link is still useless after
 * a few minutes and only ever opens the one note it was minted for.
 */
@Service
public class DownloadTicketService {

    /**
     * Long enough to survive a slow mobile hand-off (backgrounded tab, cold
     * backend, retry), short enough that a leaked URL is of little use.
     */
    private static final long TTL_SECONDS = 600;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";

    private final byte[] signingKey;

    public DownloadTicketService(@Value("${app.jwt.secret}") String secret) {
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Issues a signed, user+note-bound ticket that expires after the TTL. */
    public String issue(String userId, String noteId) {
        long expiresAt = Instant.now().getEpochSecond() + TTL_SECONDS;
        String payload = userId + SEPARATOR + noteId + SEPARATOR + expiresAt;
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    /**
     * Validates a ticket for the given note.
     *
     * <p>Not single-use on purpose: reuse within the TTL is what makes the
     * browser's extra/prefetched requests and delayed navigations harmless.
     *
     * @return the id of the user the ticket was minted for, or empty if the
     *         token is malformed, its signature does not match, it has expired,
     *         or it was minted for a different note
     */
    public Optional<String> verify(String token, String noteId) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        // Constant-time compare so the signature cannot be guessed byte by byte.
        if (!MessageDigest.isEqual(
                sign(encodedPayload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        String[] parts = payload.split("\\" + SEPARATOR, 3);
        if (parts.length != 3) {
            return Optional.empty();
        }
        String userId = parts[0];
        String ticketNoteId = parts[1];
        long expiresAt;
        try {
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        if (Instant.now().getEpochSecond() > expiresAt) {
            return Optional.empty();
        }
        // A ticket is bound to the note it was minted for, so it cannot be
        // replayed against a different PDF.
        if (!ticketNoteId.equals(noteId)) {
            return Optional.empty();
        }
        return Optional.of(userId);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return encode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign download ticket", e);
        }
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
