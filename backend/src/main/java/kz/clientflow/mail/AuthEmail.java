package kz.clientflow.mail;

/**
 * Request to send an authentication email. Published as an application event
 * and delivered only after the database transaction has committed.
 * The raw token is used to build the link and is never logged.
 */
public record AuthEmail(Type type, String to, String fullName, String language, String rawToken) {

    public enum Type {
        VERIFY_EMAIL, PASSWORD_RESET, ACCOUNT_EXISTS
    }

    @Override
    public String toString() {
        return "AuthEmail[type=" + type + "]";
    }
}
