package com.gepe.starter.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.gepe.starter.platform.web.response.ValidationError;
import com.gepe.starter.user.internal.repository.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Behavior tests of the example {@code user} module (see {@code agents.md}
 * §8): HTTP envelope contract, module-localized messages, error codes, the
 * database duplicate-key path and the event publication lifecycle. They run
 * against the locally running PostgreSQL/Redis from {@code agents.md} §5.
 *
 * <p>Asserted outcomes are observable responses (status, envelope, messages)
 * plus the database state (user row, {@code event_publication} row completed)
 * — never implementation internals.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserModuleIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String USERS_URL = "/api/v1/users";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        // Keep the shared local dev database tidy for other tests/manual runs.
        userRepository.deleteAllInBatch();
        jdbcTemplate.update("DELETE FROM event_publication");
    }

    // ------------------------------------------------------------------
    // Happy path + event publication lifecycle
    // ------------------------------------------------------------------

    @Test
    void createUserReturnsCreatedEnvelopePersistsUserAndCompletesPublication() throws Exception {
        MvcResult result = mockMvc.perform(post(USERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Budi","email":"budi@example.com"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("Budi"))
                .andExpect(jsonPath("$.data.email").value("budi@example.com"))
                .andReturn();

        // The user is persisted with the id returned in the envelope.
        String id = com.jayway.jsonpath.JsonPath
                .parse(result.getResponse().getContentAsString())
                .read("$.data.id");
        assertThat(userRepository.existsById(UUID.fromString(id))).isTrue();

        // The domain event went through the Modulith publication registry: the
        // entry was written in the same transaction and completed after the
        // AFTER_COMMIT listener ran (see agents.md §11).
        Integer completed = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM event_publication
                         WHERE event_type LIKE ? AND status = 'COMPLETED'
                           AND completion_date IS NOT NULL
                        """,
                Integer.class, "%UserCreatedEvent%");
        assertThat(completed).isEqualTo(1);
    }

    @Test
    void listUsersReturnsAllUsersOrderedByName() throws Exception {
        mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Citra","email":"citra@example.com"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Andi","email":"andi@example.com"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(get(USERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Andi"))
                .andExpect(jsonPath("$.data[1].name").value("Citra"));
    }

    // ------------------------------------------------------------------
    // Error contract: module error code, DB duplicate key, validation
    // ------------------------------------------------------------------

    @Test
    void getUnknownUserReturns404WithModuleLocalizedMessage() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(USERS_URL + "/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("user.not-found"))
                .andExpect(jsonPath("$.message").value("User with id " + unknown + " was not found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void duplicateEmailReturns409ViaDatabaseConstraint() throws Exception {
        String body = """
                {"name":"Budi","email":"sama@example.com"}""";
        mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post(USERS_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("db.duplicate_entry"))
                .andExpect(jsonPath("$.message").value("Duplicate data found"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void invalidRequestBodyReturns400WithPerFieldModuleMessages() throws Exception {
        MvcResult result = mockMvc.perform(post(USERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":""}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andReturn();

        // Messages resolve from the module bundle i18n/user/ through the
        // aggregated MessageSource (module keys referenced by the annotations).
        assertThat(fieldErrors(result))
                .containsExactlyInAnyOrder(
                        new ValidationError("name", "Name is required"),
                        new ValidationError("email", "Email is required"));
    }

    @Test
    void invalidEmailFormatReturns400WithModuleLocalizedMessage() throws Exception {
        MvcResult result = mockMvc.perform(post(USERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Budi","email":"not-an-email"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andReturn();

        assertThat(fieldErrors(result))
                .containsExactly(new ValidationError("email", "Email format is invalid"));
    }

    @Test
    void malformedUuidPathVariableReturns400InvalidParamValue() throws Exception {
        mockMvc.perform(get(USERS_URL + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("http.invalid_param_value"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Parses the {@code errors} array of an error envelope into validation errors. */
    private static List<ValidationError> fieldErrors(MvcResult result) throws Exception {
        JsonNode errors = MAPPER.readTree(result.getResponse().getContentAsString()).path("errors");
        List<ValidationError> parsed = new ArrayList<>();
        if (errors.isArray()) {
            for (JsonNode error : errors) {
                parsed.add(new ValidationError(
                        error.path("field").asText(), error.path("message").asText()));
            }
        }
        return parsed;
    }
}
