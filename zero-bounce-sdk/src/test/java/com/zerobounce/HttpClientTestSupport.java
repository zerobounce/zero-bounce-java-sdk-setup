package com.zerobounce;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

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

    /**
     * {@code Mockito.mockStatic(HttpClients.class)}, dropping the SDK's shared
     * clients when it opens and when it closes. Otherwise a client built before
     * the mock would bypass it, and one built from the mock would outlive it.
     */
    @SuppressWarnings("unchecked")
    static MockedStatic<HttpClients> mockClients() {
        SdkHttp.closeSharedClients();
        MockedStatic<HttpClients> mocked = Mockito.mockStatic(HttpClients.class);
        return (MockedStatic<HttpClients>) Proxy.newProxyInstance(MockedStatic.class.getClassLoader(),
                new Class<?>[]{MockedStatic.class}, (proxy, method, args) -> {
                    try {
                        return method.invoke(mocked, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    } finally {
                        if (method.getName().startsWith("close")) {
                            SdkHttp.closeSharedClients();
                        }
                    }
                });
    }
}
