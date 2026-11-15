package com.wjx871.population.system;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.ServletException;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
class RequestTraceFilterTest {
    @AfterEach void cleanup() { ThreadContext.clearMap(); }

    @Test void propagatesTraceAndRestoresThreadContextAfterRequest() throws Exception {
        ThreadContext.put("outer", "preserved");
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-123");
        var response = new MockHttpServletResponse();
        new RequestTraceFilter("backend-1").doFilter(request, response, (req, res) -> {
            assertThat(ThreadContext.get("traceId")).isEqualTo("trace-123");
            assertThat(ThreadContext.get("instanceId")).isEqualTo("backend-1");
        });
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(response.getHeader("X-Instance-Id")).isEqualTo("backend-1");
        assertThat(ThreadContext.getImmutableContext()).containsOnlyKeys("outer");
    }

    @Test void rejectsLogInjectionAndCleansUpOnException() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "bad\nheader");
        var response = new MockHttpServletResponse();
        assertThatThrownBy(() -> new RequestTraceFilter("local").doFilter(request, response,
                (req, res) -> { throw new ServletException("test"); })).isInstanceOf(ServletException.class);
        assertThat(response.getHeader("X-Trace-Id")).matches("[a-f0-9-]{36}");
        assertThat(ThreadContext.getImmutableContext()).isEmpty();
    }
}
