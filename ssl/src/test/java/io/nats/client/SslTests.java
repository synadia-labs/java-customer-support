// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client;

import io.nats.client.ssl.ExpiringClientCertUtil;
import io.nats.client.ssl.SslTestingHelper;
import io.nats.client.utils.NatsTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
        ExpiringClientCertUtil.Result result = ExpiringClientCertUtil.createExpired();
        validateExpiry(result, true);

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        SslTestErrorListener el = new SslTestErrorListener(1);

        try (NatsTestServer ts = new NatsTestServer(configFilePath, SHOW_SERVER)) {
            Options options = new Options.Builder()
                .server(ts.getNatsLocalhostUri())
                .sslContext(result.sslContext)
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

    @Test
    public void testReconnectFailsAfterCertExpires() throws Exception {
        ExpiringClientCertUtil.Result result = ExpiringClientCertUtil.create(5000);
        validateExpiry(result, false);

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        SslTestConnectionListener cl = new SslTestConnectionListener(1);
        SslTestErrorListener el = new SslTestErrorListener(2);

        Connection nc;
        try (NatsTestServer ts1 = new NatsTestServer(configFilePath, SHOW_SERVER)) {
            try (NatsTestServer ts2 = new NatsTestServer(configFilePath, SHOW_SERVER)) {
                Options options = new Options.Builder()
                    .servers(new String[]{ts2.getNatsLocalhostUri(), ts1.getNatsLocalhostUri()})
                    .noRandomize()
                    .sslContext(result.sslContext)
                    .maxReconnects(1)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .connectionListener(cl)
                    .errorListener(el)
                    .build();

                nc = Nats.connect(options);
                assertEquals(Connection.Status.CONNECTED, nc.getStatus());
                assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                sleep(5000); // sleep enough time for the cert to expire
                validateExpiry(result, true);
            }
            assertTrue(el.latch.await(2, TimeUnit.SECONDS));
            nc.close();
        }
        sleep(200);

        assertEquals(1, cl.count);
        assertEquals(2, el.exceptions.size());
    }

    @Test
    public void testForceReconnectFailsAfterCertExpires() throws Exception {
        ExpiringClientCertUtil.Result result = ExpiringClientCertUtil.create(5000);
        validateExpiry(result, false);

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        SslTestConnectionListener cl = new SslTestConnectionListener(1);
        SslTestErrorListener el = new SslTestErrorListener(2);

        try (NatsTestServer ts = new NatsTestServer(configFilePath, SHOW_SERVER)) {
            Options options = new Options.Builder()
                .server(ts.getNatsLocalhostUri())
                .sslContext(result.sslContext)
                .maxReconnects(1)
                .connectionTimeout(Duration.ofSeconds(5))
                .connectionListener(cl)
                .errorListener(el)
                .build();

            try (Connection nc = Nats.connect(options)) {
                assertEquals(Connection.Status.CONNECTED, nc.getStatus());
                assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                sleep(5000); // sleep enough time for the cert to expire
                validateExpiry(result, true);
                nc.forceReconnect();
                assertTrue(el.latch.await(2, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, cl.count);
        assertEquals(2, el.exceptions.size());
    }

    private void validateExpiry(ExpiringClientCertUtil.Result result, boolean expired) {
        Date expiry = result.sslContext.getClientCertificateExpiry();
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
        public int count;
        public CountDownLatch latch;

        public SslTestConnectionListener(int latchAmount) {
            count = 0;
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void connectionEvent(Connection conn, Events type) {
            if (type == Events.CONNECTED) {
                count++;
                latch.countDown();
            }
        }
    }

    class SslTestErrorListener implements ErrorListener {
        public List<Exception> exceptions;
        public CountDownLatch latch;

        public SslTestErrorListener(int latchAmount) {
            exceptions = new ArrayList<>();
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void exceptionOccurred(Connection conn, Exception exp) {
            report("exceptionOccurred: \"" + exp + "\"");
            if (hasSslOrSocketCauseInChain(exp)) {
                exceptions.add(exp);
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
}