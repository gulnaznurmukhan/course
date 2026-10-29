package kz.clientflow.mail;

/** Notification that someone was invited to a workspace. Sent after commit. */
public record InvitationEmail(String to, String workspaceName, String inviterName, String language) {

    @Override
    public String toString() {
        return "InvitationEmail";
    }
}
