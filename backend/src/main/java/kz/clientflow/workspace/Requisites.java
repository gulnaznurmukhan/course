package kz.clientflow.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Company requisites for invoices. Every field is optional. */
@Embeddable
public record Requisites(
        @Column(name = "legal_name") @Size(max = 200) String legalName,
        @Column(name = "bin") @Pattern(regexp = "[0-9]{12}") String bin,
        @Column(name = "address") @Size(max = 300) String address,
        @Column(name = "phone") @Size(max = 30) @Pattern(regexp = "[0-9+()\\- ]*") String phone,
        @Column(name = "email") @Email @Size(max = 254) String email,
        @Column(name = "bank_name") @Size(max = 150) String bankName,
        // Lower case and spaces are accepted on input; normalized() produces the strict form
        @Column(name = "iban") @Pattern(regexp = "(?i)[a-z]{2}[0-9a-z ]{10,40}") String iban,
        @Column(name = "bik") @Pattern(regexp = "(?i)[a-z0-9 ]{8,13}") String bik) {

    public static Requisites empty() {
        return new Requisites(null, null, null, null, null, null, null, null);
    }

    /** Blank strings become null, IBAN/BIK are upper-cased without spaces. */
    public Requisites normalized() {
        return new Requisites(clean(legalName), clean(bin), clean(address), clean(phone), clean(email),
                clean(bankName), upper(iban), upper(bik));
    }

    /** Strict format check of the normalized values (same rules as the database). */
    public boolean isValidNormalized() {
        return (iban == null || iban.matches("[A-Z]{2}[0-9A-Z]{10,32}"))
                && (bik == null || bik.matches("[A-Z0-9]{8,11}"));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String upper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.replace(" ", "").toUpperCase();
    }
}
