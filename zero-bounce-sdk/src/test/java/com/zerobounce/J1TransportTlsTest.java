package com.zerobounce;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The real pool's connect and socket timeouts on a TLS handshake that never completes,
 * and the JVM's TLS system properties, which the pool must honour (the client does).
 */
class J1TransportTlsTest {

    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    Path tmp;

    private final List<String> properties = new ArrayList<>();

    @AfterEach
    void restore() {
        SdkHttp.closeSharedClients();
        for (String name : properties) {
            System.clearProperty(name);
        }
        properties.clear();
    }

    /**
     * M04, M05: a server that accepts the TCP connection and never answers the TLS
     * handshake must be abandoned at the 500 ms timeout the caller asked for.
     */
    @Test
    void silentTlsHandshakeIsAbandonedAtTheCallersTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            List<Socket> held = new ArrayList<>();
            Thread acceptor = new Thread(() -> {
                try {
                    while (true) {
                        held.add(silent.accept());
                    }
                } catch (IOException closed) {
                    // the test is over
                }
            }, "j1-silent-tls");
            acceptor.setDaemon(true);
            acceptor.start();
            URI uri = URI.create("https://127.0.0.1:" + silent.getLocalPort() + "/v2/getcredits?api_key=tls-key");
            IOException error = assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () ->
                    assertThrows(IOException.class, () -> SdkHttp.exchange("GET", uri, null, null, null, 500)));
            assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("timed out"), error.getMessage());
            assertTrue(!error.getMessage().contains("tls-key"), error.getMessage());
            for (Socket socket : held) {
                socket.close();
            }
        }
    }

    /**
     * M11: a server that speaks only TLS 1.2, with the client told (https.protocols) to
     * offer only TLS 1.3. The pool must honour the system property, so the handshake
     * fails on the protocol. Without it the client offers 1.2 too, the handshake goes on
     * to the certificate check and fails there instead.
     */
    @Test
    void poolHonoursTheHttpsProtocolsSystemProperty() throws Exception {
        SSLContext context = selfSignedContext();
        try (J1TransportServer server = J1TransportServer.tls(context, "TLSv1.2",
                (request, out) -> false, 60_000)) {
            URI uri = URI.create("https://127.0.0.1:" + server.port() + "/v2/getcredits?api_key=tls-key");

            SdkHttp.closeSharedClients();
            String withoutProperty = failure(uri);
            assertTrue(withoutProperty.contains("PKIX") || withoutProperty.contains("certification"),
                    "control should reach the certificate check: " + withoutProperty);

            properties.add("https.protocols");
            System.setProperty("https.protocols", "TLSv1.3");
            SdkHttp.closeSharedClients();
            String withProperty = failure(uri);
            assertTrue(withProperty.toLowerCase(Locale.ROOT).contains("protocol"),
                    "https.protocols=TLSv1.3 was not honoured: " + withProperty);
        }
    }

    private static String failure(URI uri) {
        IOException error = assertThrows(IOException.class, () ->
                SdkHttp.exchange("GET", uri, null, null, null, 5_000));
        return error.toString();
    }

    private SSLContext selfSignedContext() throws Exception {
        Path store = tmp.resolve("server.p12");
        Path keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool");
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "j1", "-keyalg", "RSA",
                "-keysize", "2048", "-dname", "CN=localhost", "-validity", "2", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", STORE_PASSWORD, "-keypass", STORE_PASSWORD)
                .redirectErrorStream(true)
                .start();
        try (InputStream output = process.getInputStream()) {
            output.readAllBytes();
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0, "keytool failed");
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(store)) {
            keys.load(in, STORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(keys, STORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, null);
        return context;
    }
}
