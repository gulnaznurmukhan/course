package kz.clientflow.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * The signed-in user, stored in the server-side session.
 * Contains no password hash and no roles: roles are always checked against
 * the database, so changes take effect immediately.
 * getName() returns the user id, which lets us find and revoke all sessions of a user.
 */
public record CurrentUser(UUID id, String email) implements Principal, Serializable {

    @Override
    public String getName() {
        return id.toString();
    }
}
