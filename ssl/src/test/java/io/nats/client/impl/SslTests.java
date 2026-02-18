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

package io.nats.client.impl;

import io.nats.client.*;
import io.nats.client.utils.ExpiringClientCertUtil;
import io.nats.client.utils.SwitchableSSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import javax.net.ssl.SSLException;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nats.client.utils.TestBase.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class SslTests {

    static boolean SHOW_SERVER = false;

    static {
        String env = System.getenv("SSLTESTS.SHOW.SERVER");
        if (env != null && env.equalsIgnoreCase("true")) {
            SHOW_SERVER = true;
        }
        //noinspection ConstantValue
        if (SHOW_SERVER) {
            NatsTestServer.verbose();
        }
        System.out.println("JNats Version: " + Nats.CLIENT_VERSION);
    }

    static TestInfo CURRENT_INFO;

    @BeforeEach
    public void beforeEach(TestInfo testInfo) {
        CURRENT_INFO = testInfo;
    }

    @Test
    public void testConnectFailsAfterInitialConnect() throws Exception {
        SwitchableSSLContext switchableSslContext = SwitchableSSLContext.create();

        AtomicInteger connects = new AtomicInteger(0);
        CountDownLatch connectLatch = new CountDownLatch(1);
        ConnectionListener cl = (conn, type) -> {
            if (type == ConnectionListener.Events.CONNECTED) {
                connects.incrementAndGet();
                connectLatch.countDown();
            }
        };

        CountDownLatch exceptionLatch = new CountDownLatch(2);
        ErrorListener el = new ErrorListener() {
            @Override
            public void exceptionOccurred(Connection conn, Exception exp) {
                if (hasSslOrSocketCauseInChain(exp)) {
                    exceptionLatch.countDown();
                }
            }
        };

        try (NatsTestServer ts = new NatsTestServer("src/test/resources/tls.conf", SHOW_SERVER)) {
            Options options = new Options.Builder()
                .server(ts.getNatsLocalhostUri())
                .sslContext(switchableSslContext)
                .maxReconnects(1)
                .connectionTimeout(Duration.ofSeconds(5))
                .connectionListener(cl)
                .errorListener(el)
                .build();

            try (Connection nc = Nats.connect(options)) {
                assertEquals(Connection.Status.CONNECTED, nc.getStatus());
                assertTrue(connectLatch.await(2, TimeUnit.SECONDS));
                switchableSslContext.changeToFailMode();
                nc.forceReconnect();
                assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));
            }
        }

        assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));
        assertEquals(1, connects.get());
    }

    @Test
    public void testConnectFailsCertAlreadyExpired() throws Exception {
        ExpiringClientCertUtil.Result result = ExpiringClientCertUtil.createExpired();

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        CountDownLatch exceptionLatch = new CountDownLatch(1);
        ErrorListener el = new ErrorListener() {
            @Override
            public void exceptionOccurred(Connection conn, Exception exp) {
                if (hasSslOrSocketCauseInChain(exp)) {
                    exceptionLatch.countDown();
                }
            }
        };

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

            assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testReconnectFailsAfterCertExpires() throws Exception {
        ExpiringClientCertUtil.Result result =
            ExpiringClientCertUtil.create(3000);

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        AtomicInteger connects = new AtomicInteger(0);
        CountDownLatch connectLatch = new CountDownLatch(1);
        ConnectionListener cl = (conn, type) -> {
            if (type == ConnectionListener.Events.CONNECTED) {
                connects.incrementAndGet();
                connectLatch.countDown();
            }
        };

        List<Exception> exceptions = new ArrayList<>();
        CountDownLatch exceptionLatch = new CountDownLatch(2);
        ErrorListener el = new ErrorListener() {
            @Override
            public void exceptionOccurred(Connection conn, Exception exp) {
                if (hasSslOrSocketCauseInChain(exp)) {
                    exceptions.add(exp);
                    if (exceptionLatch.getCount() > 0) {
                        exceptionLatch.countDown();
                    }
                }
            }
        };


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
                assertTrue(connectLatch.await(2, TimeUnit.SECONDS));
                sleep(3500); // sleep enough time for the cert to expire
            }
            assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));
            nc.close();
        }
        sleep(200);

        assertEquals(1, connects.get());
        assertEquals(2, exceptions.size());
    }

    @Test
    public void testForceReconnectFailsAfterCertExpires() throws Exception {
        ExpiringClientCertUtil.Result result =
            ExpiringClientCertUtil.create(3000);

        Path tmpDir = Files.createTempDirectory(null).toAbsolutePath();
        String configFilePath = result.writeNatsConfig(tmpDir);

        AtomicInteger connects = new AtomicInteger(0);
        CountDownLatch connectLatch = new CountDownLatch(1);
        ConnectionListener cl = new ConnectionListener() {
            @Override
            public void connectionEvent(Connection conn, Events type) {
                if (type == Events.CONNECTED) {
                    connects.incrementAndGet();
                    connectLatch.countDown();
                }
            }
        };

        List<Exception> exceptions = new ArrayList<>();
        CountDownLatch exceptionLatch = new CountDownLatch(2);
        ErrorListener el = new ErrorListener() {
            @Override
            public void exceptionOccurred(Connection conn, Exception exp) {
                if (hasSslOrSocketCauseInChain(exp)) {
                    exceptions.add(exp);
                    exceptionLatch.countDown();
                }
            }
        };

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
                assertTrue(connectLatch.await(2, TimeUnit.SECONDS));
                sleep(3500); // sleep enough time for the cert to expire
                nc.forceReconnect();
                assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, connects.get());
        assertEquals(2, exceptions.size());
    }

    static boolean hasSslOrSocketCauseInChain(Throwable t) {
        while (t != null) {
            if (t instanceof SSLException || t instanceof SocketException) {
                System.out.println(CURRENT_INFO.getDisplayName() + ": " + t);
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}