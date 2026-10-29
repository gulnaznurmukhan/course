package kz.clientflow.client.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.clientflow.client.Client;

/** Request and response bodies of the clients API. */
public final class ClientDtos {

    private ClientDtos() {
    }

    /** Create / update body. Only these fields can be set; workspace and archive state cannot. */
    public record ClientRequest(
            @NotBlank @Size(max = 150) String name,
            @Email @Size(max = 254) String email,
            @Size(max = 30) @Pattern(regexp = "[0-9+()\\- ]*") String phone,
            @Size(max = 150) String company,
            @Size(max = 300) String address,
            @Size(max = 5000) String notes) {
    }

    public record ClientResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String company,
            String address,
            String notes,
            boolean archived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt) {

        public static ClientResponse from(Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCompany(),
                    c.getAddress(), c.getNotes(), c.isArchived(), c.getArchivedAt(), c.getCreatedAt(),
                    c.getUpdatedAt());
        }
    }

    /** Client card with order counters. */
    public record ClientDetailsResponse(ClientResponse client, long totalOrders, long activeOrders) {
    }

    /** Short info about a possible duplicate contact. */
    public record DuplicateClient(UUID id, String name, String email, String phone, boolean archived) {

        public static DuplicateClient from(Client c) {
            return new DuplicateClient(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.isArchived());
        }
    }

    public enum StatusFilter {
        ACTIVE, ARCHIVED, ALL
    }

    public enum SortField {
        NAME("name"), CREATED_AT("createdAt"), UPDATED_AT("updatedAt");

        private final String property;

        SortField(String property) {
            this.property = property;
        }

        public String property() {
            return property;
        }
    }
}
