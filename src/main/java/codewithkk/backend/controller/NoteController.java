package codewithkk.backend.controller;

import codewithkk.backend.dto.NoteSummaryResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.Note;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.BundlePurchaseRepository;
import codewithkk.backend.repository.UserRepository;
import codewithkk.backend.service.DownloadTicketService;
import codewithkk.backend.service.FileUploadService;
import codewithkk.backend.service.NoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "*")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BundlePurchaseRepository bundlePurchaseRepository;

    @Autowired
    private DownloadTicketService downloadTicketService;

    // ---------- public catalogue (no pdfUrl, ever) ----------

    @GetMapping
    public List<NoteSummaryResponse> getAllNotes() {
        return noteService.getAllNotes().stream()
                .map(NoteSummaryResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteSummaryResponse> getNoteById(@PathVariable String id) {
        Note note = noteService.getNoteById(id);
        if (note == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(NoteSummaryResponse.from(note));
    }

    // ---------- protected delivery ----------

    /**
     * Mints a short-lived download ticket for a note the caller is entitled to.
     *
     * <p>Requires the same JWT + completed-purchase check as the download
     * endpoint itself, so this cannot be used to obtain access to anything the
     * caller could not already fetch.
     */
    @PostMapping("/{id}/download-ticket")
    public ResponseEntity<Map<String, Object>> createDownloadTicket(
            @PathVariable String id,
            @RequestParam(required = false) String disposition) {

        Optional<User> user = currentUser();
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!hasAccess(user.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Note note = noteService.getNoteById(id);
        if (note == null || note.getPdfUrl() == null || note.getPdfUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        String token = downloadTicketService.issue(user.get().getId(), id);

        Map<String, Object> body = new HashMap<>();
        body.put("url", "/api/notes/" + id + "/download?ticket=" + token);
        body.put("filename", sanitizeFilename(note.getTitle()) + ".pdf");
        body.put("expiresInSeconds", 120);
        // Lets the client ask for inline rendering instead of a forced download.
        // Android WebViews cannot display PDFs, so callers there should keep the
        // default "attachment" and let the OS save the file.
        body.put("disposition", "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment");
        return ResponseEntity.ok(body);
    }

    /**
     * Streams the PDF only to a buyer. The storage URL is never disclosed, and
     * the bytes are piped straight through instead of being buffered in heap,
     * which matters on a 512MB instance.
     *
     * <p>Accepts either the bearer token (desktop, axios) or a single-use
     * download ticket (mobile, plain navigation). Both paths re-check the
     * purchase on every request.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadNote(
            @PathVariable String id,
            @RequestParam(required = false) String ticket,
            @RequestParam(required = false) String disposition) {

        Optional<User> user = currentUser();

        if (ticket != null && !ticket.isBlank()) {
            Optional<DownloadTicketService.Ticket> valid =
                    downloadTicketService.consume(ticket, id);
            if (valid.isEmpty()) {
                // Expired, already used, or forged. Never falls back to trusting
                // the ticket's contents.
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            // Re-verify entitlement rather than trusting the ticket alone, so a
            // revoked user cannot use a ticket minted moments earlier.
            user = userRepository.findById(valid.get().userId());
            if (user.isEmpty() || !hasAccess(user.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            if (user.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!hasAccess(user.get())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        Note note = noteService.getNoteById(id);
        if (note == null || note.getPdfUrl() == null || note.getPdfUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        final String filename = sanitizeFilename(note.getTitle()) + ".pdf";
        final boolean inline = "inline".equalsIgnoreCase(disposition);
        StreamingResponseBody body = out -> copyTo(note.getPdfUrl(), out);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        (inline ? "inline" : "attachment") + "; filename=\"" + filename
                                + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    private Optional<User> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(auth.getName());
    }

    /** Admins keep access by role; everyone else needs a completed purchase. */
    private boolean hasAccess(User user) {
        if (user.getRole() != null && user.getRole().equals("ROLE_ADMIN")) {
            return true;
        }
        // listByUserId rather than findByUserId: a user with more than one
        // purchase row would make the Optional-returning finder throw.
        return bundlePurchaseRepository.listByUserId(user.getId()).stream()
                .anyMatch(p -> "completed".equals(p.getStatus()));
    }

    /** Copies from local disk or a remote store without ever holding the whole file. */
    private void copyTo(String pdfUrl, java.io.OutputStream out) throws java.io.IOException {
        if (pdfUrl.startsWith("/api/files/")) {
            String name = pdfUrl.substring(pdfUrl.lastIndexOf('/') + 1);
            Resource resource = fileUploadService.getFile(name);
            try (InputStream in = resource.getInputStream()) {
                in.transferTo(out);
            }
            return;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(pdfUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        try (InputStream in = conn.getInputStream()) {
            in.transferTo(out);
        } finally {
            conn.disconnect();
        }
    }

    private String sanitizeFilename(String title) {
        String t = title == null || title.isBlank() ? "notes" : title.trim();
        t = t.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        t = t.replaceAll("\\s+", "_");
        t = t.replaceAll("_+", "_");
        return t.replaceAll("^_+|_+$", "");
    }
}
