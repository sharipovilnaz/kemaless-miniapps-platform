package com.kemaless.kemalessbank.common;

import java.net.URI;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException ex) {
        log.warn("Ошибка входа: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException ex) {
        log.warn("Некорректный запрос: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        log.warn("Конфликт данных: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        log.warn("Данные не найдены: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    ProblemDetail usernameNotFound(UsernameNotFoundException ex) {
        log.warn("Пользователь не найден: {}", ex.getMessage());
        return problem(HttpStatus.UNAUTHORIZED, "Сессия недействительна. Войдите в банк заново");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        log.warn("Ошибка валидации запроса: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Проверьте заполненные поля");
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fields.put(fieldError.getField(), readableFieldError(fieldError));
        }
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException ex) {
        log.warn("Не передан обязательный параметр: {}", ex.getParameterName());
        return problem(HttpStatus.BAD_REQUEST, "Не хватает обязательного параметра: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Некорректный формат параметра {}: {}", ex.getName(), ex.getValue());
        return problem(HttpStatus.BAD_REQUEST, "Некорректный формат параметра: " + ex.getName());
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dataAccess(DataAccessException ex) {
        log.error("Ошибка доступа к базе данных", ex);
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && sql.getMessage() != null) {
            String m = sql.getMessage();
            if (m.contains("bank_user_device")
                    && (m.contains("does not exist")
                            || m.contains("не существует")
                            || "42P01".equals(sql.getSQLState()))) {
                return problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Таблица устройств не создана. Перезапустите бэкенд (Liquibase создаст bank_user_device) или выполните миграции вручную.");
            }
        }
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка базы данных. Попробуйте позже.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Непредвиденная ошибка API", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить операцию. Попробуйте позже");
    }

    private String readableFieldError(FieldError fieldError) {
        String fieldName = readableFieldName(fieldError.getField());
        String code = fieldError.getCode();
        if ("NotBlank".equals(code) || "NotNull".equals(code)) {
            return fieldName + ": заполните поле";
        }
        if ("Size".equals(code)) {
            return fieldName + ": значение слишком длинное или короткое";
        }
        if ("Email".equals(code)) {
            return fieldName + ": укажите корректный email";
        }
        if ("Pattern".equals(code)) {
            return fieldName + ": неверный формат";
        }
        if ("DecimalMin".equals(code)) {
            return fieldName + ": значение должно быть больше нуля";
        }
        return fieldName + ": проверьте значение";
    }

    private String readableFieldName(String field) {
        return switch (field) {
            case "username" -> "Логин";
            case "password", "currentPassword", "newPassword" -> "Пароль";
            case "fullName" -> "ФИО";
            case "email" -> "Email";
            case "phone" -> "Телефон";
            case "title" -> "Название счета";
            case "currency" -> "Валюта";
            case "fromAccountId" -> "Счет списания";
            case "toAccountId" -> "Счет зачисления";
            case "toAccountNumber" -> "Номер счёта получателя";
            case "amount" -> "Сумма";
            case "description" -> "Назначение";
            default -> field;
        };
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(switch (status) {
            case BAD_REQUEST -> "Некорректный запрос";
            case UNAUTHORIZED -> "Требуется вход";
            case FORBIDDEN -> "Нет доступа";
            case NOT_FOUND -> "Не найдено";
            case CONFLICT -> "Конфликт данных";
            default -> "Ошибка";
        });
        return problem;
    }
}
