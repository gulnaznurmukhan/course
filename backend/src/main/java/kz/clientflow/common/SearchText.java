package kz.clientflow.common;

import java.util.Locale;

/** Helpers for case-insensitive "contains" searches with SQL LIKE. */
public final class SearchText {

    /** Escape character used in LIKE patterns built by {@link #containsPattern}. */
    public static final char ESCAPE = '\\';

    private SearchText() {
    }

    public static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * "50%_off" becomes "%50\%\_off%": the user's text is matched literally,
     * so % and _ typed by the user are not treated as wildcards.
     */
    public static String containsPattern(String text) {
        String escaped = text.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
