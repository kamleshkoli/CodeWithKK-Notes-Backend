package codewithkk.backend.controller;

import codewithkk.backend.dto.UserProfileResponse;
import codewithkk.backend.entity.User;
import codewithkk.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getMe(userDetails.getUsername()));
    }

    @GetMapping("/profile/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    /**
     * Updates the caller's own profile only. The id comes from the JWT, so a
     * request against another user's id is rejected rather than honoured, and
     * role changes are ignored on this path.
     */
    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable String userId, @RequestBody User user) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return ResponseEntity.status(401).build();
        }
        String callerId = userService.getMe(userDetails.getUsername()).getId();
        if (!callerId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(userService.updateOwnProfile(userId, userDetails.getUsername(), user));
    }
}
