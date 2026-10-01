package codewithkk.backend.controller;

import codewithkk.backend.dto.AdminSubscriptionResponse;
import codewithkk.backend.dto.StatsResponse;
import codewithkk.backend.entity.BundlePurchase;
import codewithkk.backend.entity.Note;
import codewithkk.backend.entity.User;
import codewithkk.backend.service.NoteService;
import codewithkk.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private NoteService noteService;

    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(userService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable String id, @RequestBody User user) {
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    @GetMapping("/notes")
    public ResponseEntity<List<Note>> getAllNotes() {
        return ResponseEntity.ok(noteService.getAllNotes());
    }

    @PostMapping("/notes")
    public ResponseEntity<Note> addNote(@RequestBody Note note) {
        return ResponseEntity.ok(noteService.addNote(note));
    }

    @PutMapping("/notes/{id}")
    public ResponseEntity<Note> updateNote(@PathVariable String id, @RequestBody Note note) {
        return ResponseEntity.ok(noteService.updateNote(id, note));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<String> deleteNote(@PathVariable String id) {
        noteService.deleteNote(id);
        return ResponseEntity.ok("Note deleted successfully");
    }

    @GetMapping("/payments")
    public ResponseEntity<List<BundlePurchase>> getAllPayments() {
        return ResponseEntity.ok(userService.getAllPurchases());
    }

    // ---------- access management ----------
    // The admin console called these three paths but the backend never
    // implemented them, so every call fell through to the dispatcher and
    // surfaced as a 500 "Internal server error".

    /** Users plus their current access state. */
    @GetMapping("/subscriptions")
    public ResponseEntity<List<AdminSubscriptionResponse>> getSubscriptions() {
        return ResponseEntity.ok(userService.getSubscriptions());
    }

    /**
     * Grants premium access to the user with this email. Records a completed
     * purchase so every existing access check honours it immediately.
     *
     * <p>Admin-only via the /api/admin/** rule in SecurityConfig.
     */
    @PostMapping("/grant-access")
    public ResponseEntity<AdminSubscriptionResponse> grantAccess(
            @RequestBody Map<String, String> body) {
        userService.grantAccess(body.get("email"));
        return userService.findSubscriptionByEmail(body.get("email"));
    }

    @DeleteMapping("/revoke-access/{userId}")
    public ResponseEntity<Map<String, Object>> revokeAccess(@PathVariable String userId) {
        userService.revokeAccess(userId);
        return ResponseEntity.ok(Map.of("revoked", true, "userId", userId));
    }
}
