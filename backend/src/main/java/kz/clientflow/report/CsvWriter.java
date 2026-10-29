package kz.clientflow.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer.
 *
 * Security: a cell that starts with = + - @ or a control character is prefixed
 * with an apostrophe, so spreadsheet programs show it as text instead of
 * executing it as a formula (CSV / formula injection).
 */
public class CsvWriter implements AutoCloseable {

    private final Writer out;

    public CsvWriter(OutputStream stream) throws IOException {
        this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        // Byte order mark: lets Excel recognise UTF-8 (Kazakh and Russian text)
        out.write('﻿');
    }

    public void row(List<?> cells) {
        try {
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(escape(cells.get(i)));
            }
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }
}
