package com.mumbai.evacuation.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimitFilterTest {

    @Test
    void chatIsLimitedPerClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true);
        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            lastStatus = response.getStatus();
            if (i < 10) assertEquals(200, lastStatus, "request " + (i + 1) + " should pass");
        }
        assertEquals(429, lastStatus);

        // A different client is unaffected.
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/chat");
        other.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(other, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void unlimitedPathsAreNotFiltered() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true);
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", "/api/shelters"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }
}
