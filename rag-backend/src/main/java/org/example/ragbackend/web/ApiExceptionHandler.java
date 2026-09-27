package org.example.ragbackend.web;

import dev.langchain4j.exception.LangChain4jException;
import lombok.extern.slf4j.Slf4j;
import org.example.ragbackend.rag.RagIngestionService.UnsupportedDocumentTypeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.stream.Collectors;

/** Traduit les erreurs métier en réponses RFC 7807 lisibles par le front. */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ProblemDetail unsupported(UnsupportedDocumentTypeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " : " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Fichier trop volumineux");
    }

    /**
     * Erreurs du fournisseur d'IA (clé invalide, quota, réseau...) → 502 ;
     * tout le reste → 500 générique, sans fuite de stack trace.
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail unexpected(RuntimeException e) {
        if (isAiProviderFailure(e)) {
            log.error("AI provider call failed", e);
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                    "Le fournisseur d'IA est indisponible ou a refusé la requête (OPENAI_API_KEY configurée ?).");
        }
        log.error("Unexpected error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne inattendue");
    }

    static boolean isAiProviderFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof LangChain4jException || t instanceof IOException) {
                return true;
            }
        }
        return false;
    }
}
