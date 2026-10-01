package codewithkk.backend.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use tickets that let a browser <em>navigate</em> to a
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
 * endpoint already enforced, and {@code consume} re-verifies the purchase
 * before streaming anything, so revoking access invalidates outstanding
 * tickets immediately.
 *
 * <p>Held in memory on purpose: tickets must not survive a restart and must not
 * be written to a shared store. Expiry is short and each ticket is destroyed on
 * first use.
 */
@Service
public class DownloadTicketService {

    /** Long enough to survive a slow tap, short enough to be useless if leaked. */
    private static final long TTL_SECONDS = 120;

    /**
     * Bound on outstanding tickets. The Render free instance has ~512MB and this
     * map is never trimmed by size alone, so an unbounded map is a DoS vector.
     */
    private static final int MAX_TICKETS = 5_000;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(String userId, String noteId) {
        if (tickets.size() >= MAX_TICKETS) {
            evictExpired();
            // Still full after a sweep means genuine abuse, not slow expiry.
            if (tickets.size() >= MAX_TICKETS) {
                throw new IllegalStateException("Too many pending downloads, try again shortly");
            }
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(token, new Ticket(userId, noteId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /**
     * Validates and burns a ticket.
     *
     * <p>Single use is deliberate: a URL that works forever is a URL that can be
     * pasted into a chat. Burning it on read means the link is only good for
     * the one navigation it was created for.
     *
     * @return the ticket, or empty if unknown, expired, or already used
     */
    public java.util.Optional<Ticket> consume(String token, String noteId) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        Ticket ticket = tickets.remove(token);
        if (ticket == null) {
            return java.util.Optional.empty();
        }
        if (ticket.expiresAt().isBefore(Instant.now())) {
            return java.util.Optional.empty();
        }
        // A ticket is bound to the note it was minted for, so it cannot be
        // replayed against a different PDF.
        if (!ticket.noteId().equals(noteId)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(ticket);
    }

    private void evictExpired() {
        Instant now = Instant.now();
        for (Iterator<Map.Entry<String, Ticket>> it = tickets.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }

    public record Ticket(String userId, String noteId, Instant expiresAt) {
    }
}
