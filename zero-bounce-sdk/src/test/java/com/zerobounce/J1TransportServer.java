package com.zerobounce;

import javax.net.ssl.SSLServerSocket;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A loopback HTTP/1.1 server that keeps connections open between requests, for
 * tests of the pooled connections the real client reuses. After each response it
 * waits {@code idleCloseMillis} for the next request on the same socket and then
 * closes it (counted by {@link #serverIdleClosed()}). A close by the client is
 * counted by {@link #peerClosed()}. Helper only, not a test class.
 */
final class J1TransportServer implements AutoCloseable {

    /** One request as it arrived. */
    static final class Req {
        final String method;
        final String target;
        final Map<String, String> headers;
        final byte[] body;

        Req(String method, String target, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.target = target;
            this.headers = headers;
            this.body = body;
        }
    }

    /**
     * Writes the response. Returns true to keep the connection for another request,
     * false to close it after writing (a body cut short closes it this way).
     */
    interface Responder {
        boolean respond(Req request, OutputStream out) throws IOException;
    }

    private final ServerSocket server;
    private final Responder responder;
    private final long idleCloseMillis;
    private final List<Req> requests = Collections.synchronizedList(new ArrayList<>());
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger peerClosed = new AtomicInteger();
    private final AtomicInteger serverIdleClosed = new AtomicInteger();

    private J1TransportServer(ServerSocket server, Responder responder, long idleCloseMillis) {
        this.server = server;
        this.responder = responder;
        this.idleCloseMillis = idleCloseMillis;
        Thread accept = new Thread(this::acceptLoop, "j1-transport-accept");
        accept.setDaemon(true);
        accept.start();
    }

    /** A plain-HTTP server on a loopback port. */
    static J1TransportServer plain(Responder responder, long idleCloseMillis) throws IOException {
        return new J1TransportServer(new ServerSocket(0, 50, InetAddress.getLoopbackAddress()),
                responder, idleCloseMillis);
    }

    /** A TLS server on a loopback port, speaking only the given protocol. */
    static J1TransportServer tls(javax.net.ssl.SSLContext context, String protocol, Responder responder,
            long idleCloseMillis) throws IOException {
        SSLServerSocket socket = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 50, InetAddress.getLoopbackAddress());
        socket.setEnabledProtocols(new String[]{protocol});
        return new J1TransportServer(socket, responder, idleCloseMillis);
    }

    int port() {
        return server.getLocalPort();
    }

    List<Req> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    int accepted() {
        return accepted.get();
    }

    /** Connections the client closed (the server read end of file). */
    int peerClosed() {
        return peerClosed.get();
    }

    /** Connections the server closed itself after {@code idleCloseMillis} without a request. */
    int serverIdleClosed() {
        return serverIdleClosed.get();
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            Socket socket;
            try {
                socket = server.accept();
            } catch (IOException e) {
                return;
            }
            accepted.incrementAndGet();
            sockets.add(socket);
            Thread handler = new Thread(() -> handle(socket), "j1-transport-conn");
            handler.setDaemon(true);
            handler.start();
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            while (true) {
                socket.setSoTimeout((int) idleCloseMillis);
                Req request;
                try {
                    request = readRequest(in);
                } catch (SocketTimeoutException idle) {
                    serverIdleClosed.incrementAndGet();
                    return;
                } catch (IOException reset) {
                    peerClosed.incrementAndGet();
                    return;
                }
                if (request == null) {
                    peerClosed.incrementAndGet();
                    return;
                }
                requests.add(request);
                socket.setSoTimeout(10_000);
                boolean keepOpen = responder.respond(request, out);
                out.flush();
                if (!keepOpen) {
                    return;
                }
            }
        } catch (IOException ignored) {
            // the client went away mid-response
        } finally {
            sockets.remove(socket);
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

    private static Req readRequest(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        String length = headers.get("content-length");
        byte[] body = length == null ? new byte[0] : in.readNBytes(Integer.parseInt(length));
        return new Req(parts[0], parts.length > 1 ? parts[1] : "", headers, body);
    }

    @Override
    public void close() throws IOException {
        server.close();
        for (Socket socket : sockets) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    // -- responses -------------------------------------------------------------

    /** A complete HTTP/1.1 response that leaves the connection open (no Connection header). */
    static boolean keepAlive(OutputStream out, int status, String reason, String contentType, byte[] body,
            String... extraHeaders) throws IOException {
        StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        if (contentType != null) {
            head.append("Content-Type: ").append(contentType).append("\r\n");
        }
        for (String header : extraHeaders) {
            head.append(header).append("\r\n");
        }
        head.append("Content-Length: ").append(body.length).append("\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        return true;
    }

    static final byte[] CREDITS = "{\"Credits\": \"5\"}".getBytes(StandardCharsets.UTF_8);
}
