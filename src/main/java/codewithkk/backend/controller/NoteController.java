package codewithkk.backend.controller;

import codewithkk.backend.dto.NoteSummaryResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.Note;
import codewithkk.backend.entity.User;
import codewithkk.backend.repository.BundlePurchaseRepository;
import codewithkk.backend.repository.UserRepository;
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
import java.util.List;
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
     * Streams the PDF only to a buyer. The storage URL is never disclosed, and
     * the bytes are piped straight through instead of being buffered in heap,
     * which matters on a 512MB instance.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> downloadNote(@PathVariable String id) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<User> user = userRepository.findByEmail(auth.getName());
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = user.get().getRole() != null
                && user.get().getRole().equals("ROLE_ADMIN");

        if (!isAdmin) {
            Optional<BundlePurchase> purchase =
                    bundlePurchaseRepository.findByUserId(user.get().getId());
            if (purchase.isEmpty() || !"completed".equals(purchase.get().getStatus())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }

        Note note = noteService.getNoteById(id);
        if (note == null || note.getPdfUrl() == null || note.getPdfUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }

        final String filename = sanitizeFilename(note.getTitle()) + ".pdf";
        StreamingResponseBody body = out -> copyTo(note.getPdfUrl(), out);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(body);
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
