package cloud.gehan.globle.web;

import cloud.gehan.globle.service.GlobleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Turns failures in Globle's API into {@code {"error": "<code>"}} with a fitting status,
 * and nothing more: no message, no exception name, no stack trace, never the answer.
 *
 * <p>Scoped with {@code assignableTypes} to Globle's own controller. An unscoped
 * advice would start answering for every controller in the app, which is precisely
 * the kind of reach a removable feature must not have.
 */
@RestControllerAdvice(assignableTypes = GlobleApiController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleApiErrors {

    private static final Logger log = LoggerFactory.getLogger(GlobleApiErrors.class);

    @ExceptionHandler(GlobleException.class)
    public ResponseEntity<Map<String, String>> refused(GlobleException e) {
        return body(e.status(), e.code());
    }

    /** Malformed JSON, a missing body, a non-numeric id, the wrong content type. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Map<String, String>> malformed(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "bad-request");
    }

    /** Anything unexpected is logged here in full and reported to the client as a bare 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Globle API request failed", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "server");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String code) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", code));
    }
}
