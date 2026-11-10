package com.example.enrollment.interfaces.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SecurityJson {
    private final ObjectMapper mapper;

    public SecurityJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void error(
            HttpServletRequest req,
            HttpServletResponse res,
            int status,
            String code,
            String message)
            throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        mapper.writeValue(
                res.getWriter(), new ApiError(code, message, Map.of(), RequestIdFilter.id(req)));
    }

    public void value(HttpServletResponse res, Object value) throws IOException {
        res.setStatus(200);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        mapper.writeValue(res.getWriter(), value);
    }
}
