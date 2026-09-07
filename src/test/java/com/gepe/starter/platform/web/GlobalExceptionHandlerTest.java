package com.gepe.starter.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import com.gepe.starter.platform.exception.ErrorCode;
import com.gepe.starter.platform.exception.GlobalError;
import com.gepe.starter.platform.exception.ServiceException;
import com.gepe.starter.platform.exception.ValidationException;
import com.gepe.starter.platform.i18n.I18nConfig;
import com.gepe.starter.platform.i18n.MessageHelper;
import com.gepe.starter.platform.web.response.ErrorResponse;
import com.gepe.starter.platform.web.response.ValidationError;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import lombok.Getter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Behavior tests for {@link GlobalExceptionHandler} (see §8 of {@code agents.md}):
 * end-to-end scenarios through standalone MockMvc and direct handler calls for
 * the mappings that need no servlet flow. All assertions check status code and
 * the localized error envelope — never implementation internals.
 *
 * <p>Envelope contract under test (see §6): every failure carries a stable
 * {@code code} (= the resolved message key) and a localized {@code message};
 * {@code errors} is present only for per-field validation failures.
 *
 * <p>Standalone (no Spring context) keeps the tests free of Postgres/Redis.
 * The validator is wired the same way Boot configures it: message lookup through
 * the aggregated MessageSource with the request locale.
 *
 * <p>{@link StubModuleError} mirrors the per-module convention of §6 — a real
 * feature module declares its own {@link ErrorCode} enum next to its bundle
 * ({@code i18n/<module>/messages.properties}) instead of reusing a global one.
 */
class GlobalExceptionHandlerTest {

    private static MessageSource messages;

    @BeforeAll
    static void buildAggregatedSource() throws IOException {
        messages = new I18nConfig().messageSource();
    }

    private MockMvc mvc() throws Exception {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages);
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler(new MessageHelper(messages)))
                .setValidator(validator)
                .build();
    }

    @Test
    void acceptsValidBody() throws Exception {
        mvc().perform(post("/stub/echo")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"gepe\",\"email\":\"gepe@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsLocalizedFieldErrorsForInvalidBody() throws Exception {
        mvc().perform(post("/stub/echo")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gepe@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void validationMessageFallsBackToEnglishForUnsupportedLocale() throws Exception {
        mvc().perform(post("/stub/echo")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "id-ID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gepe@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void mapsMalformedJsonToBadRequest() throws Exception {
        mvc().perform(post("/stub/echo")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("http.bad_request"))
                .andExpect(jsonPath("$.message").value("Bad request"));
    }

    @Test
    void mapsTypeMismatchToBadRequestWithParameterName() throws Exception {
        mvc().perform(get("/stub/type")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("http.invalid_param_value"))
                .andExpect(jsonPath("$.message").value("page has invalid value"));
    }

    @Test
    void mapsMissingParameterToBadRequestWithParameterName() throws Exception {
        mvc().perform(get("/stub/type").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("http.parameter_required"))
                .andExpect(jsonPath("$.message").value("page parameter is required"));
    }

    @Test
    void mapsNotFoundServiceExceptionTo404WithModuleMessage() throws Exception {
        mvc().perform(get("/stub/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("testmodule.not-found"))
                .andExpect(jsonPath("$.message").value("User with id 42 was not found"));
    }

    @Test
    void mapsConflictServiceExceptionTo409() throws Exception {
        mvc().perform(get("/stub/conflict").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("db.duplicate_entry"))
                .andExpect(jsonPath("$.message").value("Duplicate data found"));
    }

    @Test
    void mapsMethodNotAllowedTo405() throws Exception {
        mvc().perform(post("/stub/not-found").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("http.method_not_allowed"))
                .andExpect(jsonPath("$.message").value("Method not allowed"));
    }

    // ------------------------------------------------------------------
    // Direct handler calls for the mappings that do not need a servlet flow
    // ------------------------------------------------------------------

    @Nested
    class DirectHandlerMappings {

        private final GlobalExceptionHandler handler =
                new GlobalExceptionHandler(new MessageHelper(messages));

        @Test
        void mapsNoResourceFoundTo404() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                        new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown", ""));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().code()).isEqualTo("http.not_found");
                assertThat(response.getBody().message()).isEqualTo("Resource not found");
                assertThat(response.getBody().errors()).isNull();
                return null;
            });
        }

        @Test
        void mapsDuplicateKeyTo409() {
            withEnglishLocale(() -> {
                var violation = new DataIntegrityViolationException("statement",
                        new SQLException("duplicate key value violates unique constraint \"uq_name\"", "23505"));
                ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(violation);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getBody().code()).isEqualTo("db.duplicate_entry");
                assertThat(response.getBody().message()).isEqualTo("Duplicate data found");
                return null;
            });
        }

        @Test
        void mapsGenericDataIntegrityViolationTo400() {
            withEnglishLocale(() -> {
                var violation = new DataIntegrityViolationException("statement",
                        new SQLException("null value in column \"title\" violates not-null constraint", "23502"));
                ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(violation);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().code()).isEqualTo("db.data_integrity");
                assertThat(response.getBody().message()).isEqualTo("Data integrity violation");
                return null;
            });
        }

        @Test
        void mapsOptimisticLockingTo409() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response =
                        handler.handleOptimisticLocking(new OptimisticLockingFailureException("version conflict"));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getBody().code()).isEqualTo("exception.optimistic_lock");
                assertThat(response.getBody().message()).isEqualTo("Data conflict detected");
                return null;
            });
        }

        @Test
        void mapsUnsupportedMediaTypeTo415() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response =
                        handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("unsupported"));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                assertThat(response.getBody().code()).isEqualTo("http.unsupported_media_type");
                assertThat(response.getBody().message()).isEqualTo("Unsupported media type");
                return null;
            });
        }

        @Test
        void mapsNotAcceptableTo406() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response =
                        handler.handleMediaTypeNotAcceptable(new HttpMediaTypeNotAcceptableException("none"));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
                assertThat(response.getBody().code()).isEqualTo("http.not_acceptable");
                assertThat(response.getBody().message()).isEqualTo("Not acceptable");
                return null;
            });
        }

        @Test
        void mapsMaxUploadSizeTo413() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response =
                        handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1_048_576));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
                assertThat(response.getBody().code()).isEqualTo("file.too_large");
                assertThat(response.getBody().message()).isEqualTo("File size too large");
                return null;
            });
        }

        @Test
        void mapsUnexpectedFailureTo500WithoutLeakingInternals() {
            withEnglishLocale(() -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/stub/boom");
                ResponseEntity<ErrorResponse> response =
                        handler.handleUnexpected(new IllegalStateException("secret-internal-detail"), request);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(response.getBody().code()).isEqualTo("system.error");
                assertThat(response.getBody().message()).isEqualTo("Unexpected system error");
                return null;
            });
        }

        @Test
        void mapsServiceExceptionWithArgsAndModuleKey() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response = handler.handleServiceException(
                        new ServiceException(StubModuleError.NOT_FOUND, "42"));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(response.getBody().code()).isEqualTo("testmodule.not-found");
                assertThat(response.getBody().message()).isEqualTo("User with id 42 was not found");
                return null;
            });
        }

        @Test
        void mapsServiceExceptionWithSharedGlobalError() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response = handler.handleServiceException(
                        new ServiceException(GlobalError.DB_DUPLICATE_ENTRY));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getBody().code()).isEqualTo("db.duplicate_entry");
                assertThat(response.getBody().message()).isEqualTo("Duplicate data found");
                return null;
            });
        }

        @Test
        void mapsServiceLayerValidationTo400WithFields() {
            withEnglishLocale(() -> {
                ResponseEntity<ErrorResponse> response = handler.handleServiceValidation(
                        new ValidationException(List.of(new ValidationError("email", "Email is already registered"))));
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().code()).isEqualTo("validation.failed");
                assertThat(response.getBody().message()).isEqualTo("Validation failed");
                assertThat(response.getBody().errors()).hasSize(1);
                assertThat(response.getBody().errors().get(0).field()).isEqualTo("email");
                assertThat(response.getBody().errors().get(0).message()).isEqualTo("Email is already registered");
                return null;
            });
        }

        private <T> T withEnglishLocale(Supplier<T> action) {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            try {
                return action.get();
            }
            finally {
                LocaleContextHolder.resetLocaleContext();
            }
        }
    }

    /**
     * Module-scoped error codes implementing the platform {@link ErrorCode}
     * contract — exactly how a feature module declares its own errors (here it
     * only serves the stub controller of this test).
     */
    @Getter
    enum StubModuleError implements ErrorCode {

        NOT_FOUND(HttpStatus.NOT_FOUND, "testmodule.not-found");

        private final HttpStatus httpStatus;
        private final String messageKey;

        StubModuleError(HttpStatus httpStatus, String messageKey) {
            this.httpStatus = httpStatus;
            this.messageKey = messageKey;
        }
    }

    /** Minimal endpoint set that exercises each validation path and each domain exception. */
    @RestController
    static class StubController {

        @PostMapping("/stub/echo")
        Echo echo(@Valid @RequestBody EchoRequest body) {
            return new Echo(body.name());
        }

        @GetMapping("/stub/type")
        String type(@RequestParam Integer page) {
            return String.valueOf(page);
        }

        @GetMapping("/stub/not-found")
        String notFound() {
            throw new ServiceException(StubModuleError.NOT_FOUND, "42");
        }

        @GetMapping("/stub/conflict")
        String conflict() {
            throw new ServiceException(GlobalError.DB_DUPLICATE_ENTRY);
        }

        record EchoRequest(@NotBlank String name, @Email String email) {
        }

        record Echo(String name) {
        }
    }
}
