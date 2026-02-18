# SSL Test Project

Testing SSL Error Raising, Version 1.0.3 

### Prerequisites

* Java 11, 17 or 21
* Run from the ssl directory in this project.

### Classes of Interest

[ExpiringClientCertUtil.java](src/test/java/io/nats/client/ssl/ExpiringClientCertUtil.java)
allows us to build a test certificate on the fly and set its expiration.

[DiagnosticSslContext.java](src/test/java/io/nats/client/ssl/DiagnosticSslContext.java)
allows us to get information about the ssl context, such as the certificate expiry, 
which, if made available to an ErrorListener, 
can be used when a `javax.net.ssl.SSLHandshakeException` or `java.net.SocketException` is part of the exception chain,
to initiate some event.

### Environment

1\. The JNATS Java Client Version is set in the `build.gradle` and `pom.xml` as `2.20.0`
You can manually change the `build.gradle` or `pom.xml` to change this or set an environment variable, see below.
During the test run, the JNats Version will be printed.

2\. If you want full server output,
you can set `SHOW_SERVER` variable manually in the [SslTests.java](src/test/java/io/nats/client/impl/SslTests.java) class

**The environment variables...**

| OS      | JNats Version                            | Show Server Setting                |
|---------|------------------------------------------|------------------------------------|
| Unix    | `export JNATS_VERSION=major.minor.patch` | `export SSLTESTS.SHOW.SERVER=true` |
| Windows | `set JNATS_VERSION=major.minor.patch`    | `set SSLTESTS.SHOW.SERVER=true`    |

### Test class: 
[SslTests.java](src/test/java/io/nats/client/impl/SslTests.java)

### Command Line

#### Gradle
> Note: Gradle 8.14 or later is required.

Run all tests:
```
gradlew test
```

Run individual tests
```
gradlew test --tests SslTests.testConnectFailsFromSslContext
gradlew test --tests SslTests.testConnectFailsCertAlreadyExpired
gradlew test --tests SslTests.testReconnectFailsAfterCertExpires
gradlew test --tests SslTests.testForceReconnectFailsAfterCertExpires
```

#### Maven

Run all tests:
```
mvn test
```

Run individual tests
```
mvn -Dtest=SslTests#testConnectFailsFromSslContext test
mvn -Dtest=SslTests#testConnectFailsCertAlreadyExpired test
mvn -Dtest=SslTests#testReconnectFailsAfterCertExpires test
mvn -Dtest=SslTests#testForceReconnectFailsAfterCertExpires test
```

### Example output

All tests with (not showing server output)

```
TEST: [testConnectFailsFromSslContext] START TEST
TEST: [testConnectFailsFromSslContext] exceptionOccurred: "java.util.concurrent.ExecutionException: javax.net.ssl.SSLHandshakeException: Fail mode: all certificates rejected"
TEST: [testConnectFailsFromSslContext] END TEST

TEST: [testForceReconnectFailsAfterCertExpires] START TEST
TEST: [testForceReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:54:17 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:54:13 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:54:17 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:54:18 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
TEST: [testForceReconnectFailsAfterCertExpires] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
TEST: [testForceReconnectFailsAfterCertExpires] END TEST

TEST: [testConnectFailsCertAlreadyExpired] START TEST
TEST: [testConnectFailsCertAlreadyExpired] Certificate Expiry: Wed Feb 18 16:54:20 EST 2026
TEST: [testConnectFailsCertAlreadyExpired] Current Time      : Wed Feb 18 17:54:20 EST 2026
TEST: [testConnectFailsCertAlreadyExpired] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
TEST: [testConnectFailsCertAlreadyExpired] END TEST

TEST: [testReconnectFailsAfterCertExpires] START TEST
TEST: [testReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:54:26 EST 2026
TEST: [testReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:54:21 EST 2026
TEST: [testReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:54:26 EST 2026
TEST: [testReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:54:26 EST 2026
TEST: [testReconnectFailsAfterCertExpires] exceptionOccurred: "java.net.SocketException: Connection reset"
TEST: [testReconnectFailsAfterCertExpires] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
TEST: [testReconnectFailsAfterCertExpires] END TEST
```

testForceReconnectFailsAfterCertExpires test showing server output

```
TEST: [testForceReconnectFailsAfterCertExpires] START TEST
TEST: [testForceReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:56:36 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:56:32 EST 2026
INFO: %%% Starting [nats-server --config C:\Users\batman\AppData\Local\Temp\nats_java_test16609940957229005984.conf -DV] with redirected IO
INFO: [54664] 2026/02/18 17:56:32.177437 [INF] Starting nats-server
INFO: [54664] 2026/02/18 17:56:32.177437 [INF]   Version:  2.14.0-dev
INFO: [54664] 2026/02/18 17:56:32.177437 [INF]   Git:      [not set]
INFO: [54664] 2026/02/18 17:56:32.177437 [DBG]   Go build: go1.25.0
INFO: [54664] 2026/02/18 17:56:32.177437 [INF]   Name:     NABFYH3HRYXHQLLEJY747EOKOYSWYW4YEIRFEFBXUWVYEIF6Z5K2NV4O
INFO: [54664] 2026/02/18 17:56:32.177437 [INF]   ID:       NABFYH3HRYXHQLLEJY747EOKOYSWYW4YEIRFEFBXUWVYEIF6Z5K2NV4O
INFO: [54664] 2026/02/18 17:56:32.177437 [INF] Using configuration file: C:\Users\batman\AppData\Local\Temp\nats_java_test16609940957229005984.conf (sha256:deee1f5b5a376f4beacbea5a4aede5e84c194df2d48475dae143588c736e6215)
INFO: [54664] 2026/02/18 17:56:32.177437 [DBG] Created system account: "$SYS"
INFO: [54664] 2026/02/18 17:56:32.188959 [INF] Listening for client connections on localhost:57363
INFO: [54664] 2026/02/18 17:56:32.188959 [INF] TLS required for client connections
INFO: [54664] 2026/02/18 17:56:32.188959 [INF] Server is ready
INFO: [54664] 2026/02/18 17:56:32.188959 [DBG] maxprocs: Leaving GOMAXPROCS=24: CPU quota undefined
INFO: [54664] 2026/02/18 17:56:32.329086 [DBG] 127.0.0.1:57365 - cid:5 - Client connection created
INFO: [54664] 2026/02/18 17:56:32.329086 [DBG] 127.0.0.1:57365 - cid:5 - Starting TLS client connection handshake
INFO: [54664] 2026/02/18 17:56:32.329086 [ERR] 127.0.0.1:57365 - cid:5 - TLS handshake error: tls: first record does not look like a TLS handshake
INFO: [54664] 2026/02/18 17:56:32.329086 [DBG] 127.0.0.1:57365 - cid:5 - Client connection closed: TLS Handshake Failure
INFO: %%% Started [nats-server --config C:\Users\batman\AppData\Local\Temp\nats_java_test16609940957229005984.conf -DV]
INFO: [54664] 2026/02/18 17:56:32.356353 [DBG] 127.0.0.1:57366 - cid:6 - Client connection created
INFO: [54664] 2026/02/18 17:56:32.356353 [DBG] 127.0.0.1:57366 - cid:6 - Starting TLS client connection handshake
INFO: [54664] 2026/02/18 17:56:32.396057 [DBG] 127.0.0.1:57366 - cid:6 - TLS handshake complete
INFO: [54664] 2026/02/18 17:56:32.396057 [DBG] 127.0.0.1:57366 - cid:6 - TLS version 1.2, cipher suite TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
INFO: [54664] 2026/02/18 17:56:32.397580 [TRC] 127.0.0.1:57366 - cid:6 - <<- [CONNECT {"lang":"java","version":"development","protocol":1,"verbose":false,"pedantic":false,"tls_required":true,"echo":true,"headers":true,"no_responders":true}]
INFO: [54664] 2026/02/18 17:56:32.398943 [TRC] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - <<- [PING]
INFO: [54664] 2026/02/18 17:56:32.398943 [TRC] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - ->> [PONG]
INFO: [54664] 2026/02/18 17:56:34.786973 [DBG] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - Client Ping Timer
INFO: [54664] 2026/02/18 17:56:34.786973 [TRC] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - ->> [PING]
INFO: [54664] 2026/02/18 17:56:34.787511 [TRC] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - <<- [PONG]
TEST: [testForceReconnectFailsAfterCertExpires] Certificate Expiry: Wed Feb 18 17:56:36 EST 2026
TEST: [testForceReconnectFailsAfterCertExpires] Current Time      : Wed Feb 18 17:56:37 EST 2026
INFO: [54664] 2026/02/18 17:56:37.404592 [DBG] 127.0.0.1:57366 - cid:6 - "vdevelopment:java" - Client connection closed: Client Closed
INFO: [54664] 2026/02/18 17:56:37.404592 [DBG] 127.0.0.1:57372 - cid:7 - Client connection created
INFO: [54664] 2026/02/18 17:56:37.404592 [DBG] 127.0.0.1:57372 - cid:7 - Starting TLS client connection handshake
INFO: [54664] 2026/02/18 17:56:37.407468 [ERR] 127.0.0.1:57372 - cid:7 - TLS handshake error: tls: failed to verify certificate: x509: certificate has expired or is not yet valid: current time 2026-02-18T17:56:37-05:00 is after 2026-02-18T22:56:36Z (CN=Test Client,O=NATS Test SHA-256: ff088036958cf731b8e04bb1b8bc0652786fc2b646820f1cb1ad1d2055c88779; CN=Test Client CA,O=NATS Test SHA-256: 42ef4aa69d580c65d979836c1be022d02f90c2a46aa9dea95acff73f6f35c0fe)
INFO: [54664] 2026/02/18 17:56:37.407468 [DBG] 127.0.0.1:57372 - cid:7 - Client connection closed: TLS Handshake Failure
TEST: [testForceReconnectFailsAfterCertExpires] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
INFO: [54664] 2026/02/18 17:56:39.491338 [DBG] 127.0.0.1:57378 - cid:8 - Client connection created
INFO: [54664] 2026/02/18 17:56:39.491338 [DBG] 127.0.0.1:57378 - cid:8 - Starting TLS client connection handshake
INFO: [54664] 2026/02/18 17:56:39.494648 [ERR] 127.0.0.1:57378 - cid:8 - TLS handshake error: tls: failed to verify certificate: x509: certificate has expired or is not yet valid: current time 2026-02-18T17:56:39-05:00 is after 2026-02-18T22:56:36Z (CN=Test Client,O=NATS Test SHA-256: ff088036958cf731b8e04bb1b8bc0652786fc2b646820f1cb1ad1d2055c88779; CN=Test Client CA,O=NATS Test SHA-256: 42ef4aa69d580c65d979836c1be022d02f90c2a46aa9dea95acff73f6f35c0fe)
INFO: [54664] 2026/02/18 17:56:39.494648 [DBG] 127.0.0.1:57378 - cid:8 - Client connection closed: TLS Handshake Failure
TEST: [testForceReconnectFailsAfterCertExpires] exceptionOccurred: "java.util.concurrent.ExecutionException: java.net.SocketException: An established connection was aborted by the software in your host machine"
INFO: %%% Shut down [nats-server --config C:\Users\batman\AppData\Local\Temp\nats_java_test16609940957229005984.conf -DV]
TEST: [testForceReconnectFailsAfterCertExpires] END TEST
```