package com.kemaless.core.auth.dto.api;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
public class ErrorDto {
    private int status;
    private String error;
    private String message;
    private String path;

    @Builder.Default
    private OffsetDateTime timestamp = OffsetDateTime.now();

    private List<FieldErrorDto> validationErrors;

    @Data
    @Builder
    public static class FieldErrorDto {
        private String field;
        private String message;
    }
}
