package com.zerobounce;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * A real HTTP/1.1 server on a loopback socket, answering every connection with
 * raw bytes the test chooses: a body cut short of its {@code Content-Length}, a
 * redirect, or nothing at all. The SDK's real HttpClient 5 exchanges are routed
 * here by {@link #apacheClients()}. Nothing reaches the live API.
 */
final class LoopbackServer implements AutoCloseable {

    /** One request as it arrived. */
    static final class Request {
        String method;
        String target;
        final Map<String, String> headers = new LinkedHashMap<>();
        byte[] body = new byte[0];

        @Override
        public String toString() {
            return method + " " + target;
        }
    }

    /** Writes the raw response. Returning without writing anything closes the connection. */
    interface Responder {
        void respond(Request request, OutputStream out) throws IOException;
    }

    private final ServerSocket server;
    private final Responder responder;
    private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch closed = new CountDownLatch(1);

    /** The request config of every request the clients from {@link #apacheClients()} sent. */
    final List<RequestConfig> apacheConfigs = Collections.synchronizedList(new ArrayList<>());

    LoopbackServer(Responder responder) throws IOException {
        this.responder = responder;
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread accept = new Thread(this::acceptLoop, "loopback-accept");
        accept.setDaemon(true);
        accept.start();
    }

    int port() {
        return server.getLocalPort();
    }

    List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    void clear() {
        requests.clear();
        apacheConfigs.clear();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                return;
            }
            sockets.add(socket);
            Thread handler = new Thread(() -> handle(socket), "loopback-conn");
            handler.setDaemon(true);
            handler.start();
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            Request request = readRequest(in);
            if (request == null) {
                return;
            }
            requests.add(request);
            OutputStream out = socket.getOutputStream();
            responder.respond(request, out);
            out.flush();
            socket.shutdownOutput();
        } catch (IOException ignored) {
            // The client gave up (a timeout) or the test closed the server.
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        if (b < 0 && line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }

    private static Request readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        Request request = new Request();
        String[] parts = requestLine.split(" ");
        request.method = parts[0];
        request.target = parts.length > 1 ? parts[1] : "";
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                request.headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        // Read the whole request body first, so the client is never cut off
        // while it is still sending (a multipart upload, say).
        String length = request.headers.get("content-length");
        if (length != null) {
            request.body = in.readNBytes(Integer.parseInt(length));
        } else if ("chunked".equalsIgnoreCase(request.headers.get("transfer-encoding"))) {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            while (true) {
                String size = readLine(in);
                if (size == null) {
                    break;
                }
                int semicolon = size.indexOf(';');
                int chunk = Integer.parseInt((semicolon < 0 ? size : size.substring(0, semicolon)).trim(), 16);
                if (chunk == 0) {
                    while ((line = readLine(in)) != null && !line.isEmpty()) {
                        // trailers
                    }
                    break;
                }
                body.write(in.readNBytes(chunk));
                readLine(in);
            }
            request.body = body.toByteArray();
        }
        return request;
    }

    @Override
    public void close() throws IOException {
        closed.countDown();
        server.close();
        synchronized (sockets) {
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // already closed
                }
            }
        }
    }

    // -- responders ------------------------------------------------------------

    static byte[] head(int status, String reason, Map<String, String> headers) {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        return head.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** A complete response. */
    static Responder answer(int status, String contentType, byte[] body) {
        return (request, out) -> {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", contentType);
            headers.put("Content-Length", String.valueOf(body.length));
            out.write(head(status, "OK", headers));
            out.write(body);
        };
    }

    /** A 200 whose header declares {@code declared} bytes, then {@code body} and a closed connection. */
    static Responder cutShort(String contentType, byte[] body, int declared) {
        return (request, out) -> {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", contentType);
            headers.put("Content-Length", String.valueOf(declared));
            out.write(head(200, "OK", headers));
            out.write(body);
        };
    }

    /** A redirect with a Location header, so a client that follows redirects would go there. */
    static Responder redirect(int status, String location) {
        return (request, out) -> {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Location", location);
            headers.put("Content-Length", "0");
            out.write(head(status, "Redirect", headers));
        };
    }

    /** A server that reads each request and never answers it, until it is closed. */
    static LoopbackServer silent() throws IOException {
        LoopbackServer[] server = new LoopbackServer[1];
        server[0] = new LoopbackServer((request, out) -> {
            try {
                server[0].closed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return server[0];
    }

    // -- clients wired to this server -------------------------------------------

    /** The same path and query, on this server over plain http. */
    String local(String url) {
        URI uri = URI.create(url);
        String host = uri.getHost();
        if ("127.0.0.1".equals(host)) {
            return url;
        }
        return "http://127.0.0.1:" + port() + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    /**
     * A {@link ZeroBounceSDK} whose ordinary requests come here. Its
     * Apache HttpClient requests need {@link #apacheClients()} as well.
     */
    ZeroBounceSDK sdk() throws Exception {
        ZeroBounceSDK sdk = spy(new ZeroBounceSDK());
        doAnswer(invocation -> URI.create(local(invocation.getArgument(0))).toURL()).when(sdk).createUrlFrom(anyString());
        // The spy copied the original's Priority3Api, which still calls the
        // original's createUrlFrom.
        Field field = ZeroBounceSDK.class.getDeclaredField("priority3Api");
        field.setAccessible(true);
        field.set(sdk, new Priority3Api(sdk));
        return sdk;
    }

    ZeroBounceWarmup warmup() throws Exception {
        ZeroBounceWarmup warmup = spy(new ZeroBounceWarmup());
        doAnswer(invocation -> URI.create(local(invocation.getArgument(0))).toURL()).when(warmup).createUrlFrom(anyString());
        return warmup;
    }

    /**
     * Until closed, every {@code HttpClients.custom()} on this thread
     * returns a real builder routed to this server (or to the loopback host and
     * port a redirect names), which records each request's config.
     */
    MockedStatic<HttpClients> apacheClients() {
        MockedStatic<HttpClients> clients = Mockito.mockStatic(HttpClients.class);
        clients.when(HttpClients::custom).thenAnswer(invocation -> HttpClientBuilder.create()
                .setRoutePlanner((target, context) -> new HttpRoute(new HttpHost("http", "127.0.0.1",
                        "127.0.0.1".equals(target.getHostName()) ? target.getPort() : port())))
                .addRequestInterceptorLast((HttpRequestInterceptor) (request, entity, context) ->
                        apacheConfigs.add(HttpClientContext.cast(context).getRequestConfig())));
        return clients;
    }
}
