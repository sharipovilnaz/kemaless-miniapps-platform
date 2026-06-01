package com.kemaless.paymentminiapp.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final ObjectMapper objectMapper;

    public ApiExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Некорректные данные");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(RestClientResponseException.class)
    ProblemDetail upstream(RestClientResponseException e) {
        HttpStatusCode code = e.getStatusCode();
        HttpStatus status = HttpStatus.resolve(code.value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String body = e.getResponseBodyAsString();
        String detail = extractDetail(body);
        if (detail == null || detail.isBlank()) {
            detail = "Банк вернул " + code.value();
        }
        return ProblemDetail.forStatusAndDetail(status, truncate(detail, 800));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private String extractDetail(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(body);
            if (n.hasNonNull("detail")) {
                return n.get("detail").asText();
            }
            if (n.hasNonNull("message")) {
                return n.get("message").asText();
            }
            if (n.hasNonNull("title")) {
                return n.get("title").asText();
            }
        } catch (Exception ignored) {
        }
        return body;
    }
}
