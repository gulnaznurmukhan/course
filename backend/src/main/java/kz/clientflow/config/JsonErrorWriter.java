package kz.clientflow.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import kz.clientflow.common.ApiError;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes {@link ApiError} bodies from Spring Security filters, which run
 * before controllers and therefore bypass the GlobalExceptionHandler.
 */
@Component
public class JsonErrorWriter {

    private final JsonMapper jsonMapper;

    public JsonErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiError.of(status.value(), code));
    }
}
