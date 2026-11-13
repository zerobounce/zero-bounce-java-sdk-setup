package com.zerobounce;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Only the HTTP client factory is replaced; SDK response handling still runs. */
final class HttpClientTestSupport {
    static HttpClientBuilder builder(CloseableHttpClient client) {
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        return builder;
    }
}
