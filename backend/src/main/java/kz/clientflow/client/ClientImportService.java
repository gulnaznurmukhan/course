package kz.clientflow.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kz.clientflow.activity.ActivityLog;
import kz.clientflow.activity.ActivityService;
import kz.clientflow.client.dto.ClientDtos.ClientRequest;
import kz.clientflow.common.ApiException;
import kz.clientflow.workspace.WorkspaceAccess;

/**
 * Imports clients from a CSV file in two steps: a preview (dryRun) that validates
 * every row and finds duplicates, then the import of the valid, non-duplicate rows
 * in one transaction (all or nothing).
 */
@Service
public class ClientImportService {

    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 1000;

    /** Accepted column titles in English, Russian and Kazakh. */
    private static final Map<String, String> HEADERS = Map.ofEntries(
            Map.entry("name", "name"), Map.entry("имя", "name"), Map.entry("название", "name"),
            Map.entry("клиент", "name"), Map.entry("аты", "name"),
            Map.entry("email", "email"), Map.entry("e-mail", "email"), Map.entry("почта", "email"),
            Map.entry("phone", "phone"), Map.entry("телефон", "phone"),
            Map.entry("company", "company"), Map.entry("компания", "company"),
            Map.entry("address", "address"), Map.entry("адрес", "address"), Map.entry("мекенжай", "address"),
            Map.entry("notes", "notes"), Map.entry("заметки", "notes"), Map.entry("примечание", "notes"),
            Map.entry("жазбалар", "notes"));

    public enum RowStatus {
        VALID, INVALID, DUPLICATE
    }

    public record RowResult(int line, String name, String email, String phone, RowStatus status,
                            List<String> errors, String duplicateOf) {
    }

    public record ImportResult(int total, int valid, int invalid, int duplicates, int imported, List<RowResult> rows) {
    }

    private final ClientRepository clients;
    private final Validator validator;
    private final ActivityService activity;

    public ClientImportService(ClientRepository clients, Validator validator, ActivityService activity) {
        this.clients = clients;
        this.validator = validator;
        this.activity = activity;
    }

    @Transactional
    public ImportResult importCsv(WorkspaceAccess access, byte[] data, boolean dryRun) {
        if (!access.role().canManageRecords()) {
            throw ApiException.forbidden();
        }
        if (data.length == 0 || data.length > MAX_BYTES) {
            throw ApiException.badRequest("IMPORT_FILE_SIZE");
        }
        List<List<String>> table = CsvParser.parse(CsvParser.decode(data));
        if (table.isEmpty()) {
            throw ApiException.badRequest("IMPORT_EMPTY");
        }
        Map<String, Integer> columns = mapHeader(table.getFirst());
        if (!columns.containsKey("name")) {
            throw ApiException.badRequest("IMPORT_NO_NAME_COLUMN");
        }
        List<List<String>> body = table.subList(1, table.size());
        if (body.size() > MAX_ROWS) {
            throw ApiException.badRequest("IMPORT_TOO_MANY_ROWS");
        }

        Set<String> emailsInFile = new HashSet<>();
        Set<String> phonesInFile = new HashSet<>();
        List<RowResult> results = new ArrayList<>();
        List<ClientRequest> toImport = new ArrayList<>();

        for (int i = 0; i < body.size(); i++) {
            List<String> cells = body.get(i);
            ClientRequest request = new ClientRequest(cell(cells, columns, "name"), cell(cells, columns, "email"),
                    cell(cells, columns, "phone"), cell(cells, columns, "company"), cell(cells, columns, "address"),
                    cell(cells, columns, "notes"));
            int line = i + 2; // line 1 is the header

            List<String> errors = validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .distinct()
                    .sorted()
                    .toList();
            if (!errors.isEmpty()) {
                results.add(new RowResult(line, request.name(), request.email(), request.phone(), RowStatus.INVALID,
                        errors, null));
                continue;
            }

            String email = request.email() == null ? null : request.email().toLowerCase(Locale.ROOT);
            String digits = request.phone() == null ? "" : request.phone().replaceAll("[^0-9]", "");
            String phoneKey = digits.length() >= 6 ? digits.substring(Math.max(0, digits.length() - 10)) : null;

            String duplicateOf = findExisting(access, email, digits);
            if (duplicateOf == null && ((email != null && emailsInFile.contains(email))
                    || (phoneKey != null && phonesInFile.contains(phoneKey)))) {
                duplicateOf = "#file";
            }
            if (email != null) {
                emailsInFile.add(email);
            }
            if (phoneKey != null) {
                phonesInFile.add(phoneKey);
            }

            if (duplicateOf != null) {
                results.add(new RowResult(line, request.name(), request.email(), request.phone(), RowStatus.DUPLICATE,
                        List.of(), duplicateOf));
            } else {
                results.add(new RowResult(line, request.name(), request.email(), request.phone(), RowStatus.VALID,
                        List.of(), null));
                toImport.add(request);
            }
        }

        int imported = 0;
        if (!dryRun) {
            for (ClientRequest request : toImport) {
                Client client = new Client(access.workspaceId());
                client.setName(request.name().trim());
                client.setEmail(request.email() == null ? null : request.email().trim().toLowerCase(Locale.ROOT));
                client.setPhone(request.phone());
                client.setCompany(request.company());
                client.setAddress(request.address());
                client.setNotes(request.notes());
                clients.save(client);
                imported++;
            }
            if (imported > 0) {
                activity.record(access, "CLIENTS_IMPORTED", ActivityLog.EntityType.CLIENT, null,
                        Map.of("count", imported));
            }
        }

        int invalid = (int) results.stream().filter(r -> r.status() == RowStatus.INVALID).count();
        int duplicates = (int) results.stream().filter(r -> r.status() == RowStatus.DUPLICATE).count();
        return new ImportResult(results.size(), toImport.size(), invalid, duplicates, imported, results);
    }

    private String findExisting(WorkspaceAccess access, String email, String phoneDigits) {
        if (email != null) {
            var byEmail = clients.findByEmail(access.workspaceId(), email, Limit.of(1));
            if (!byEmail.isEmpty()) {
                return byEmail.getFirst().getName();
            }
        }
        if (phoneDigits.length() >= 6) {
            var byPhone = clients.findByPhoneDigits(access.workspaceId(), phoneDigits);
            if (!byPhone.isEmpty()) {
                return byPhone.getFirst().getName();
            }
        }
        return null;
    }

    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = HEADERS.get(header.get(i).trim().toLowerCase(Locale.ROOT));
            if (key != null) {
                columns.putIfAbsent(key, i);
            }
        }
        return columns;
    }

    /** Trimmed cell value; empty cells become null. */
    private static String cell(List<String> cells, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index).trim();
        return value.isEmpty() ? null : value;
    }
}
