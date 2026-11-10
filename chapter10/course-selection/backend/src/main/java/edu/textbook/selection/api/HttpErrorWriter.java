package edu.textbook.selection.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class HttpErrorWriter {
    public record ErrorBody(
            String code, String message, String requestId, java.util.Map<String, String> fields) {
        public ErrorBody(String code, String message, String requestId) {
            this(code, message, requestId, java.util.Map.of());
        }
    }

    private final ObjectMapper json;

    public HttpErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        json.writeValue(
                response.getOutputStream(),
                new ErrorBody(code, message, RequestIdFilter.id(request)));
    }
}
