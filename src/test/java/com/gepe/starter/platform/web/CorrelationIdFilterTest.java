package com.gepe.starter.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gepe.starter.platform.logging.MdcKeys;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Behavior tests for {@link CorrelationIdFilter}: header echo, MDC populated
 * during the request, and MDC cleaned up afterwards.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new StubController())
                .addFilters(filter)
                .build();
    }

    @Test
    void echoesIncomingRequestIdAndKeepsItInMdcDuringRequest() throws Exception {
        mockMvc().perform(get("/stub").header(CorrelationIdFilter.CORRELATION_ID_HEADER, "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "req-123"))
                .andExpect(header().string("X-MDC-RequestId", "req-123"));

        // MDC must be clean once the request is over.
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void generatesRequestIdWhenHeaderIsMissing() throws Exception {
        MvcResult result = mockMvc().perform(get("/stub"))
                .andExpect(status().isOk())
                .andReturn();

        String generated = result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(generated).isNotBlank();
        // The value seen inside the request must equal the value echoed back.
        assertThat(result.getResponse().getHeader("X-MDC-RequestId")).isEqualTo(generated);
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void generatesAUniqueRequestIdPerRequest() throws Exception {
        MockMvc mvc = mockMvc();
        String first = mvc.perform(get("/stub")).andReturn().getResponse()
                .getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        String second = mvc.perform(get("/stub")).andReturn().getResponse()
                .getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);

        assertThat(first).isNotBlank().isNotEqualTo(second);
    }

    /** Minimal endpoint that exposes the MDC value it saw while running. */
    @RestController
    static class StubController {

        @GetMapping("/stub")
        String stub(HttpServletResponse response) {
            response.setHeader("X-MDC-RequestId", MDC.get(MdcKeys.REQUEST_ID));
            return "ok";
        }
    }
}
