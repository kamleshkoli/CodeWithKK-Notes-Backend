package codewithkk.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the admin "Subscriptions" table: a user plus whether they
 * currently hold access.
 *
 * <p>Deliberately carries {@code userId} rather than reusing {@code User}, so
 * the admin console never receives password hashes and the access flag has an
 * obvious home.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSubscriptionResponse {

    private String userId;
    private String name;
    private String email;
    private String role;
    private boolean hasPremium;
}