package com.empresa.offboarding.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(
            EntityNotFoundException exception
    ) {
        log.warn(
                "Recurso no encontrado: {}",
                exception.getMessage()
        );

        return build(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(
            NoResourceFoundException exception
    ) {
        log.warn(
                "Ruta inexistente solicitada: {}",
                exception.getResourcePath()
        );

        return build(
                HttpStatus.NOT_FOUND,
                "Recurso no encontrado",
                "La ruta solicitada no existe"
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(
            AccessDeniedException exception
    ) {
        log.warn(
                "Access denied: {}",
                exception.getMessage()
        );

        return build(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "No tienes permiso para realizar esta operación"
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(
            IllegalStateException exception
    ) {
        log.warn(
                "Operación no permitida: {}",
                exception.getMessage()
        );

        return build(
                HttpStatus.CONFLICT,
                "Operación no permitida",
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(
            IllegalArgumentException exception
    ) {
        log.warn(
                "Solicitud inválida: {}",
                exception.getMessage()
        );

        return build(
                HttpStatus.BAD_REQUEST,
                "Solicitud inválida",
                exception.getMessage()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(
            DataIntegrityViolationException exception
    ) {
        log.warn(
                "Violación de integridad de datos",
                exception
        );

        return build(
                HttpStatus.CONFLICT,
                "Conflicto de datos",
                "La operación viola una restricción de integridad"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> errors =
                new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                    errors.put(
                        error.getField(),
                        error.getDefaultMessage()
                    )
                );

        String detail = errors.entrySet()
                .stream()
                .map(entry ->
                    fieldLabel(entry.getKey())
                            + ": "
                            + entry.getValue()
                )
                .collect(Collectors.joining("; "));

        if (detail.isBlank()) {
            detail =
                    "Uno o más campos contienen datos inválidos";
        }

        ProblemDetail problem = build(
                HttpStatus.BAD_REQUEST,
                "Datos inválidos",
                detail
        );

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, String> errors =
                new LinkedHashMap<>();

        exception.getConstraintViolations()
                .forEach(violation ->
                    errors.put(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                    )
                );

        String detail = errors.entrySet()
                .stream()
                .map(entry ->
                    fieldLabel(lastPath(entry.getKey()))
                            + ": "
                            + entry.getValue()
                )
                .collect(Collectors.joining("; "));

        ProblemDetail problem = build(
                HttpStatus.BAD_REQUEST,
                "Datos inválidos",
                detail.isBlank()
                        ? "Uno o más campos contienen datos inválidos"
                        : detail
        );

        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(
            Exception exception
    ) {
        log.error(
                "Error inesperado procesando la petición",
                exception
        );

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno del servidor",
                "Ocurrió un error inesperado"
        );
    }

    private String fieldLabel(String field) {
        return switch (field) {
            case "username" -> "Usuario";
            case "fullName" -> "Nombre completo";
            case "email" -> "Correo";
            case "password" -> "Contraseña";
            case "roles" -> "Perfiles";
            case "enabled" -> "Estado";
            default -> field;
        };
    }

    private String lastPath(String path) {
        int position = path.lastIndexOf('.');

        return position >= 0
                ? path.substring(position + 1)
                : path;
    }

    private ProblemDetail build(
            HttpStatus status,
            String title,
            String detail
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        detail
                );

        problem.setTitle(title);

        return problem;
    }
}
