package io.nats.client;

import io.nats.client.ssl.ExpiringClientCertUtil;
import io.nats.client.ssl.ExpiringComponents;
import io.nats.client.ssl.SslTestingHelper;
import io.nats.client.utils.NatsTestServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class SslTests {

    static boolean SHOW_SERVER = false;

    static {
        String env = System.getenv("SSLTESTS.SHOW.SERVER");
        if (env != null && env.equalsIgnoreCase("true")) {
            SHOW_SERVER = true;
        }
        if (SHOW_SERVER) {
            NatsTestServer.verbose();
        }
    }

    static String TEST_ID;

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        TEST_ID = testInfo.getDisplayName().replace("()", "");
        System.out.println();
        report("START TEST");
    }

    @AfterEach
    public void afterEach() {
        report("END TEST");
    }

    @Test
    public void testConnectFailsFromSslContext() throws Exception {
        SSLContext sslContext = SslTestingHelper.getFailContext();

        SslTestErrorListener el = new SslTestErrorListener(1);

        try (NatsTestServer ts = new NatsTestServer("src/test/resources/tls.conf", SHOW_SERVER)) {
            Options options = new Options.Builder()
                .server(ts.getNatsLocalhostUri())
                .sslContext(sslContext)
                .maxReconnects(1)
                .connectionTimeout(Duration.ofSeconds(2))
                .errorListener(el)
                .build();

            try (Connection nc = Nats.connect(options)) {
                fail("Should not have connected");
            }
            catch (Exception e) {
                assertTrue(e.getMessage().contains("Unable to connect to NATS servers"));
            }

            assertTrue(el.latch.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testConnectFailsCertAlreadyExpired() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.createExpired();
            validateExpiry(expiring, true);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestErrorListener el = new SslTestErrorListener(1);

            try (NatsTestServer ts = new NatsTestServer(configFilePath, SHOW_SERVER)) {
                Options options = new Options.Builder()
                    .server(ts.getNatsLocalhostUri())
                    .sslContext(expiring.sslContext)
                    .maxReconnects(1)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .errorListener(el)
                    .build();

                try (Connection nc = Nats.connect(options)) {
                    fail("should have thrown an exception");
                }
                catch (Exception e) {
                    assertTrue(e.getMessage().contains("Unable to connect to NATS servers"));
                }

                assertTrue(el.latch.await(2, TimeUnit.SECONDS));
            }
        }
        finally {
            deleteRecursive(tmpDir);
        }
    }

    static final int CLIENT_CERT_VALIDITY_MILLIS = 5000;

    @Test
    public void testReconnectFailsAfterCertExpires() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.create(CLIENT_CERT_VALIDITY_MILLIS);
            validateExpiry(expiring, false);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestConnectionListener cl = new SslTestConnectionListener(1);
            SslTestErrorListener el = new SslTestErrorListener(2);

            Connection nc;
            try (NatsTestServer ts1 = new NatsTestServer(configFilePath, SHOW_SERVER)) {
                try (NatsTestServer ts2 = new NatsTestServer(configFilePath, SHOW_SERVER)) {
                    Options options = new Options.Builder()
                        .servers(new String[]{ts2.getNatsLocalhostUri(), ts1.getNatsLocalhostUri()})
                        .noRandomize()
                        .sslContext(expiring.sslContext)
                        .maxReconnects(1)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .connectionListener(cl)
                        .errorListener(el)
                        .build();

                    nc = Nats.connect(options);
                    assertEquals(Connection.Status.CONNECTED, nc.getStatus());
                    assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                    sleep(CLIENT_CERT_VALIDITY_MILLIS); // sleep enough time for the cert to expire
                    validateExpiry(expiring, true);
                }
                assertTrue(el.latch.await(2, TimeUnit.SECONDS));
                nc.close();
            }
        }
        finally {
            deleteRecursive(tmpDir);
        }
    }

    @Test
    public void testForceReconnectFailsAfterCertExpires() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.create(CLIENT_CERT_VALIDITY_MILLIS);
            validateExpiry(expiring, false);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestConnectionListener cl = new SslTestConnectionListener(1);
            SslTestErrorListener el = new SslTestErrorListener(2);

            try (NatsTestServer ts = new NatsTestServer(configFilePath, SHOW_SERVER)) {
                Options options = new Options.Builder()
                    .server(ts.getNatsLocalhostUri())
                    .sslContext(expiring.sslContext)
                    .maxReconnects(1)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .connectionListener(cl)
                    .errorListener(el)
                    .build();

                try (Connection nc = Nats.connect(options)) {
                    assertEquals(Connection.Status.CONNECTED, nc.getStatus());
                    assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                    sleep(CLIENT_CERT_VALIDITY_MILLIS); // sleep enough time for the cert to expire
                    validateExpiry(expiring, true);
                    nc.forceReconnect();
                    assertTrue(el.latch.await(2, TimeUnit.SECONDS));
                }
            }
        }
        finally {
            deleteRecursive(tmpDir);
        }
    }

    private void validateExpiry(ExpiringComponents expiring, boolean expired) {
        Date expiry = expiring.sslContext.getClientCertificateExpiry();
        Date now = new Date();
        report("Certificate Expiry: " + expiry);
        report("Current Time      : " + now);
        if (expired) {
            assertTrue(expiry.before(now));
        }
        else {
            assertFalse(expiry.before(now));
        }
    }

    private void report(String s) {
        System.out.println("TEST: [" + TEST_ID + "] " + s);
    }

    static class SslTestConnectionListener implements ConnectionListener {
        public CountDownLatch latch;

        public SslTestConnectionListener(int latchAmount) {
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void connectionEvent(Connection conn, Events type) {
            if (type == Events.CONNECTED) {
                latch.countDown();
            }
        }
    }

    class SslTestErrorListener implements ErrorListener {
        public CountDownLatch latch;

        public SslTestErrorListener(int latchAmount) {
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            report("exceptionOccurred: \"" + exp + "\"");
            if (hasSslOrSocketCauseInChain(exp)) {
                if (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        }

        boolean hasSslOrSocketCauseInChain(Throwable t) {
            while (t != null) {
                if (t instanceof SSLException || t instanceof SocketException) {
                    return true;
                }
                t = t.getCause();
            }
            return false;
        }
    }

    public static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { /* ignored */ }
    }

    public static @NonNull Path createTempDirectory() throws IOException {
        return Files.createTempDirectory(null).toAbsolutePath();
    }

    public static void deleteRecursive(Path path) {
        if (path != null) {
            deleteRecursive(path.toFile());
        }
    }

    public static void deleteRecursive(@NonNull File file) {
        try {
            if (file.isDirectory()) {
                File[] entries = file.listFiles();
                if (entries != null) {
                    for (File entry : entries) {
                        deleteRecursive(entry);
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        catch (Exception ignore) {}
    }
}