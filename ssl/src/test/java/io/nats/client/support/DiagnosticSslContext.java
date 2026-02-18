package io.nats.client.support;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.Socket;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An SSLContext subclass that provides detailed diagnostic logging
 * for SSL/TLS handshake failures, certificate issues, and protocol mismatches.
 * <p>
 * When {@code init} is called, key and trust managers are automatically wrapped
 * with diagnostic versions, and context info is logged after initialization.
 * <p>
 * Usage:
 * <pre>
 *   DiagnosticSslContext ctx = DiagnosticSslContext.create("TLSv1.2");
 *   ctx.init(km, tm, random);
 *
 *   // or with a custom logger:
 *   Logger log = ConsoleLogger.getLogger("MyApp");
 *   DiagnosticSslContext ctx = DiagnosticSslContext.create(log, "TLSv1.2");
 *   ctx.init(km, tm, random);
 * </pre>
 */
public class DiagnosticSslContext extends SSLContext {

    private static Logger DEFAULT_LOG = ConsoleLogger.getLogger(DiagnosticSslContext.class);

    public static void defaultLog(Logger defaultLog) {
        DEFAULT_LOG = defaultLog;
    }

    private DiagnosticSslContext(DiagnosticSpi spi, Provider provider, String protocol) {
        super(spi, provider, protocol);
    }

    /**
     * Creates a new DiagnosticSslContext for the given protocol using a default logger.
     * Call {@code init} to initialize with key/trust managers.
     */
    public static DiagnosticSslContext create(String protocol) throws NoSuchAlgorithmException {
        return create(DEFAULT_LOG, protocol);
    }

    /**
     * Creates a new DiagnosticSslContext for the given protocol using the provided logger.
     * Call {@code init} to initialize with key/trust managers.
     */
    public static DiagnosticSslContext create(Logger log, String protocol) throws NoSuchAlgorithmException {
        SSLContext delegate = SSLContext.getInstance(protocol);
        DiagnosticSpi spi = new DiagnosticSpi(log, delegate);
        return new DiagnosticSslContext(spi, delegate.getProvider(), protocol);
    }

    /**
     * Wraps an existing, already-initialized SSLContext using a default logger.
     */
    public static DiagnosticSslContextFacade wrap(SSLContext delegate) {
        return wrap(DEFAULT_LOG, delegate);
    }

    /**
     * Wraps an existing, already-initialized SSLContext by returning a facade
     * that produces diagnostic socket factories with logging.
     */
    public static DiagnosticSslContextFacade wrap(Logger log, SSLContext delegate) {
        logContextInfo(log, delegate);
        return new DiagnosticSslContextFacade(log, delegate);
    }

    // ---- SPI that wraps managers on init and logs context info ----

    private static class DiagnosticSpi extends SSLContextSpi {
        private final Logger log;
        private final SSLContext delegate;

        DiagnosticSpi(Logger log, SSLContext delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
            throws KeyManagementException {
            KeyManager[] wrappedKm = wrapKeyManagers(log, km);
            TrustManager[] wrappedTm = wrapTrustManagers(log, tm);
            delegate.init(wrappedKm, wrappedTm, sr);
            logContextInfo(log, delegate);
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            return delegate.getSocketFactory();
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return delegate.getServerSocketFactory();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            return delegate.createSSLEngine();
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return delegate.createSSLEngine(host, port);
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            return delegate.getServerSessionContext();
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            return delegate.getClientSessionContext();
        }
    }

    // ---- Facade for wrapping an already-initialized SSLContext ----

    public static class DiagnosticSslContextFacade {
        private final Logger log;
        private final SSLContext delegate;

        DiagnosticSslContextFacade(Logger log, SSLContext delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        public SSLContext getDelegate() {
            return delegate;
        }

        public SSLSocketFactory getSocketFactory() {
            return new DiagnosticSSLSocketFactory(log, delegate.getSocketFactory());
        }

        public SSLServerSocketFactory getServerSocketFactory() {
            return new DiagnosticSSLServerSocketFactory(log, delegate.getServerSocketFactory());
        }
    }

    // ---- Diagnostic TrustManager ----

    static TrustManager[] wrapTrustManagers(Logger log, TrustManager[] originals) {
        if (originals == null) {
            try {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((KeyStore) null);
                originals = tmf.getTrustManagers();
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to load default TrustManagers", e);
                return null;
            }
        }

        TrustManager[] wrapped = new TrustManager[originals.length];
        for (int i = 0; i < originals.length; i++) {
            if (originals[i] instanceof X509TrustManager) {
                wrapped[i] = new DiagnosticX509TrustManager(log, (X509TrustManager) originals[i]);
            } else {
                wrapped[i] = originals[i];
            }
        }
        return wrapped;
    }

    static KeyManager[] wrapKeyManagers(Logger log, KeyManager[] originals) {
        if (originals == null) return null;

        KeyManager[] wrapped = new KeyManager[originals.length];
        for (int i = 0; i < originals.length; i++) {
            if (originals[i] instanceof X509KeyManager) {
                wrapped[i] = new DiagnosticX509KeyManager(log, (X509KeyManager) originals[i]);
            } else {
                wrapped[i] = originals[i];
            }
        }
        return wrapped;
    }

    // ---- DiagnosticX509TrustManager ----

    static class DiagnosticX509TrustManager implements X509TrustManager {
        private final Logger log;
        private final X509TrustManager delegate;

        DiagnosticX509TrustManager(Logger log, X509TrustManager delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
            logCertificateChain("Client", chain, authType);
            try {
                delegate.checkClientTrusted(chain, authType);
                log.fine("Client certificate trusted successfully");
            } catch (CertificateException e) {
                logCertificateFailure("Client", chain, authType, e);
                throw e;
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
            logCertificateChain("Server", chain, authType);
            try {
                delegate.checkServerTrusted(chain, authType);
                log.fine("Server certificate trusted successfully");
            } catch (CertificateException e) {
                logCertificateFailure("Server", chain, authType, e);
                throw e;
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] issuers = delegate.getAcceptedIssuers();
            log.fine(() -> "Accepted issuers count: " + (issuers != null ? issuers.length : 0));
            return issuers;
        }

        private void logCertificateChain(String side, X509Certificate[] chain, String authType) {
            if (!log.isLoggable(Level.FINE)) return;

            StringBuilder sb = new StringBuilder();
            sb.append(side).append(" certificate chain (authType=").append(authType)
                .append(", depth=").append(chain.length).append("):\n");

            for (int i = 0; i < chain.length; i++) {
                X509Certificate cert = chain[i];
                sb.append(String.format("  [%d] Subject : %s%n", i, cert.getSubjectX500Principal()));
                sb.append(String.format("       Issuer  : %s%n", cert.getIssuerX500Principal()));
                sb.append(String.format("       Serial  : %s%n", cert.getSerialNumber().toString(16)));
                sb.append(String.format("       Valid   : %s -> %s%n", cert.getNotBefore(), cert.getNotAfter()));
                sb.append(String.format("       SigAlg  : %s%n", cert.getSigAlgName()));

                try {
                    if (cert.getSubjectAlternativeNames() != null) {
                        sb.append("       SANs    : ").append(cert.getSubjectAlternativeNames()).append('\n');
                    }
                } catch (Exception ignored) {
                    // SANs not parseable
                }
            }
            log.fine(sb.toString());
        }

        private void logCertificateFailure(String side, X509Certificate[] chain,
                                           String authType, CertificateException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("*** ").append(side).append(" certificate REJECTED ***\n");
            sb.append("  Auth type : ").append(authType).append('\n');
            sb.append("  Reason    : ").append(e.getMessage()).append('\n');

            if (chain != null && chain.length > 0) {
                X509Certificate leaf = chain[0];
                sb.append("  Leaf cert : ").append(leaf.getSubjectX500Principal()).append('\n');

                try {
                    leaf.checkValidity();
                } catch (CertificateException validity) {
                    sb.append("  ** EXPIRED OR NOT YET VALID: ").append(validity.getMessage()).append('\n');
                }

                if (chain.length == 1) {
                    sb.append("  ** POSSIBLE ISSUE: Chain has only 1 certificate (self-signed or missing intermediates)\n");
                }

                if (leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) {
                    sb.append("  ** SELF-SIGNED certificate detected\n");
                }
            }

            Throwable cause = e.getCause();
            int depth = 0;
            while (cause != null && depth < 5) {
                sb.append("  Caused by [").append(depth).append("]: ")
                    .append(cause.getClass().getSimpleName())
                    .append(" - ").append(cause.getMessage()).append('\n');
                cause = cause.getCause();
                depth++;
            }

            log.warning(sb.toString());
        }
    }

    // ---- DiagnosticX509KeyManager ----

    static class DiagnosticX509KeyManager implements X509KeyManager {
        private final Logger log;
        private final X509KeyManager delegate;

        DiagnosticX509KeyManager(Logger log, X509KeyManager delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            String[] aliases = delegate.getClientAliases(keyType, issuers);
            log.fine(() -> "Client aliases for keyType=" + keyType + ": " +
                (aliases != null ? Arrays.toString(aliases) : "none"));
            return aliases;
        }

        @Override
        public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
            String alias = delegate.chooseClientAlias(keyTypes, issuers, socket);
            if (alias == null) {
                log.warning(() -> "No client alias found for keyTypes=" + Arrays.toString(keyTypes) +
                    ", issuers=" + (issuers != null ? Arrays.toString(issuers) : "any") +
                    " — client certificate authentication may fail");
            } else {
                log.fine(() -> "Chose client alias: " + alias);
            }
            return alias;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            String[] aliases = delegate.getServerAliases(keyType, issuers);
            log.fine(() -> "Server aliases for keyType=" + keyType + ": " +
                (aliases != null ? Arrays.toString(aliases) : "none"));
            return aliases;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            String alias = delegate.chooseServerAlias(keyType, issuers, socket);
            if (alias == null) {
                log.warning(() -> "No server alias for keyType=" + keyType +
                    " — server may fail to present a certificate");
            } else {
                log.fine(() -> "Chose server alias: " + alias);
            }
            return alias;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            X509Certificate[] chain = delegate.getCertificateChain(alias);
            log.fine(() -> "Certificate chain for alias '" + alias + "': " +
                (chain != null ? chain.length + " certs" : "null"));
            return chain;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            PrivateKey key = delegate.getPrivateKey(alias);
            if (key == null) {
                log.warning(() -> "No private key found for alias '" + alias + "'");
            } else {
                log.fine(() -> "Private key for alias '" + alias + "': algorithm=" + key.getAlgorithm());
            }
            return key;
        }
    }

    // ---- Diagnostic SSLSocketFactory ----

    static class DiagnosticSSLSocketFactory extends SSLSocketFactory {
        private final Logger log;
        private final SSLSocketFactory delegate;

        DiagnosticSSLSocketFactory(Logger log, SSLSocketFactory delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
            log.fine(() -> "Creating SSL socket: host=" + host + ", port=" + port);
            try {
                Socket socket = delegate.createSocket(s, host, port, autoClose);
                logSocketInfo(log, (SSLSocket) socket);
                addHandshakeListener(log, (SSLSocket) socket);
                return socket;
            } catch (IOException e) {
                log.warning(() -> "Failed to create SSL socket to " + host + ":" + port +
                    " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            log.fine(() -> "Creating SSL socket: host=" + host + ", port=" + port);
            try {
                Socket socket = delegate.createSocket(host, port);
                addHandshakeListener(log, (SSLSocket) socket);
                return socket;
            } catch (IOException e) {
                log.warning(() -> "Failed to create SSL socket to " + host + ":" + port +
                    " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }

        @Override
        public Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort)
            throws IOException {
            try {
                Socket socket = delegate.createSocket(host, port, localHost, localPort);
                addHandshakeListener(log, (SSLSocket) socket);
                return socket;
            } catch (IOException e) {
                log.warning(() -> "Failed to create SSL socket to " + host + ":" + port +
                    " from " + localHost + ":" + localPort +
                    " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }

        @Override
        public Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            try {
                Socket socket = delegate.createSocket(host, port);
                addHandshakeListener(log, (SSLSocket) socket);
                return socket;
            } catch (IOException e) {
                log.warning(() -> "Failed to create SSL socket to " + host + ":" + port +
                    " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }

        @Override
        public Socket createSocket(java.net.InetAddress address, int port,
                                   java.net.InetAddress localAddress, int localPort)
            throws IOException {
            try {
                Socket socket = delegate.createSocket(address, port, localAddress, localPort);
                addHandshakeListener(log, (SSLSocket) socket);
                return socket;
            } catch (IOException e) {
                log.warning(() -> "Failed to create SSL socket to " + address + ":" + port +
                    " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }
    }

    // ---- Diagnostic SSLServerSocketFactory ----

    static class DiagnosticSSLServerSocketFactory extends SSLServerSocketFactory {
        private final Logger log;
        private final SSLServerSocketFactory delegate;

        DiagnosticSSLServerSocketFactory(Logger log, SSLServerSocketFactory delegate) {
            this.log = log;
            this.delegate = delegate;
        }

        @Override
        public java.net.ServerSocket createServerSocket(int port) throws IOException {
            log.fine(() -> "Creating SSL server socket on port " + port);
            return delegate.createServerSocket(port);
        }

        @Override
        public java.net.ServerSocket createServerSocket(int port, int backlog) throws IOException {
            return delegate.createServerSocket(port, backlog);
        }

        @Override
        public java.net.ServerSocket createServerSocket(int port, int backlog,
                                                        java.net.InetAddress ifAddress)
            throws IOException {
            return delegate.createServerSocket(port, backlog, ifAddress);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }

    // ---- Session inspection ----

    /**
     * Returns the most recent client {@link SSLSession} from this context's session cache,
     * or {@code null} if the cache is empty.
     * <p>
     * This allows inspecting session details (certificates, protocol, cipher suite, etc.)
     * directly from the SSLContext without needing a reference to the SSLSocket.
     *
     * @param ctx the SSLContext to inspect
     * @return the most recently created client session, or null
     */
    public static SSLSession getMostRecentClientSession(SSLContext ctx) {
        SSLSessionContext sessionCtx = ctx.getClientSessionContext();
        if (sessionCtx == null) {
            return null;
        }
        Enumeration<byte[]> ids = sessionCtx.getIds();
        SSLSession newest = null;
        while (ids.hasMoreElements()) {
            byte[] id = ids.nextElement();
            SSLSession session = sessionCtx.getSession(id);
            if (session != null && (newest == null || session.getCreationTime() > newest.getCreationTime())) {
                newest = session;
            }
        }
        return newest;
    }

    /**
     * Builds a diagnostic string from the most recent client session in this context's cache.
     * Includes protocol, cipher suite, peer info, and certificate details with expiry dates.
     * Returns {@code "No client sessions in cache"} if the cache is empty.
     *
     * @param ctx the SSLContext to inspect
     * @return a formatted diagnostic string
     */
    public static String getSessionDiagnostics(SSLContext ctx) {
        SSLSession session = getMostRecentClientSession(ctx);
        if (session == null) {
            return "No client sessions in cache";
        }
        return formatSessionDiagnostics(session);
    }

    /**
     * Builds a diagnostic string from the given {@link SSLSession}.
     * Includes protocol, cipher suite, peer info, and certificate details with expiry dates.
     *
     * @param session the SSLSession to format
     * @return a formatted diagnostic string
     */
    public static String formatSessionDiagnostics(SSLSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("SSLSession diagnostics:\n");
        sb.append("  Protocol     : ").append(session.getProtocol()).append('\n');
        sb.append("  Cipher suite : ").append(session.getCipherSuite()).append('\n');
        sb.append("  Peer host    : ").append(session.getPeerHost()).append('\n');
        sb.append("  Peer port    : ").append(session.getPeerPort()).append('\n');
        sb.append("  Created      : ").append(new Date(session.getCreationTime())).append('\n');
        sb.append("  Last accessed: ").append(new Date(session.getLastAccessedTime())).append('\n');

        // Local (client) certificates
        java.security.cert.Certificate[] localCerts = session.getLocalCertificates();
        if (localCerts != null) {
            sb.append("  Local certs  : ").append(localCerts.length).append('\n');
            for (int i = 0; i < localCerts.length; i++) {
                if (localCerts[i] instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) localCerts[i];
                    sb.append(String.format("    [%d] Subject : %s%n", i, x.getSubjectX500Principal()));
                    sb.append(String.format("         Valid   : %s -> %s%n", x.getNotBefore(), x.getNotAfter()));
                    try {
                        x.checkValidity();
                        sb.append("         Status  : VALID\n");
                    } catch (CertificateException e) {
                        sb.append("         Status  : *** ").append(e.getMessage()).append(" ***\n");
                    }
                }
            }
        } else {
            sb.append("  Local certs  : none\n");
        }

        // Peer (server) certificates
        try {
            java.security.cert.Certificate[] peerCerts = session.getPeerCertificates();
            sb.append("  Peer certs   : ").append(peerCerts.length).append('\n');
            for (int i = 0; i < peerCerts.length; i++) {
                if (peerCerts[i] instanceof X509Certificate) {
                    X509Certificate x = (X509Certificate) peerCerts[i];
                    sb.append(String.format("    [%d] Subject : %s%n", i, x.getSubjectX500Principal()));
                    sb.append(String.format("         Valid   : %s -> %s%n", x.getNotBefore(), x.getNotAfter()));
                    try {
                        x.checkValidity();
                        sb.append("         Status  : VALID\n");
                    } catch (CertificateException e) {
                        sb.append("         Status  : *** ").append(e.getMessage()).append(" ***\n");
                    }
                }
            }
        } catch (SSLPeerUnverifiedException e) {
            sb.append("  Peer certs   : UNVERIFIED (").append(e.getMessage()).append(")\n");
        }

        return sb.toString();
    }

    // ---- Helpers ----

    private static void addHandshakeListener(Logger log, SSLSocket socket) {
        socket.addHandshakeCompletedListener(event -> {
            try {
                StringBuilder sb = new StringBuilder("Handshake completed:\n");
                sb.append("  Protocol   : ").append(event.getSession().getProtocol()).append('\n');
                sb.append("  Cipher     : ").append(event.getCipherSuite()).append('\n');
                sb.append("  Peer host  : ").append(event.getSession().getPeerHost()).append('\n');
                sb.append("  Peer port  : ").append(event.getSession().getPeerPort()).append('\n');

                try {
                    java.security.cert.Certificate[] peerCerts = event.getPeerCertificates();
                    if (peerCerts.length > 0 && peerCerts[0] instanceof X509Certificate) {
                        sb.append("  Peer cert  : ").append(((X509Certificate) peerCerts[0]).getSubjectX500Principal()).append('\n');
                    }
                } catch (SSLPeerUnverifiedException e) {
                    sb.append("  Peer cert  : UNVERIFIED (").append(e.getMessage()).append(")\n");
                }

                log.info(sb.toString());
            } catch (Exception e) {
                log.warning("Error logging handshake: " + e.getMessage());
            }
        });
    }

    private static void logSocketInfo(Logger log, SSLSocket socket) {
        if (!log.isLoggable(Level.FINE)) return;

        StringBuilder sb = new StringBuilder("SSL socket configuration:\n");
        sb.append("  Enabled protocols : ").append(Arrays.toString(socket.getEnabledProtocols())).append('\n');
        sb.append("  Enabled ciphers   : ").append(Arrays.toString(socket.getEnabledCipherSuites())).append('\n');
        sb.append("  Need client auth  : ").append(socket.getNeedClientAuth()).append('\n');
        sb.append("  Want client auth  : ").append(socket.getWantClientAuth()).append('\n');
        sb.append("  Use client mode   : ").append(socket.getUseClientMode()).append('\n');

        SSLParameters params = socket.getSSLParameters();
        if (params.getServerNames() != null) {
            sb.append("  SNI server names  : ").append(params.getServerNames()).append('\n');
        }
        if (params.getProtocols() != null) {
            sb.append("  Param protocols   : ").append(Arrays.toString(params.getProtocols())).append('\n');
        }
        sb.append("  Endpoint ID algo  : ").append(params.getEndpointIdentificationAlgorithm());

        log.fine(sb.toString());
    }

    private static void logContextInfo(Logger log, SSLContext ctx) {
        if (!log.isLoggable(Level.FINE)) return;

        StringBuilder sb = new StringBuilder("SSLContext info:\n");
        sb.append("  Protocol : ").append(ctx.getProtocol()).append('\n');
        sb.append("  Provider : ").append(ctx.getProvider().getName())
            .append(" v").append(ctx.getProvider().getVersion()).append('\n');

        SSLParameters defaults = ctx.getDefaultSSLParameters();
        sb.append("  Default protocols : ").append(Arrays.toString(defaults.getProtocols())).append('\n');
        sb.append("  Default ciphers   : ").append(Arrays.toString(defaults.getCipherSuites())).append('\n');

        SSLParameters supported = ctx.getSupportedSSLParameters();
        sb.append("  Supported protocols: ").append(Arrays.toString(supported.getProtocols())).append('\n');

        log.fine(sb.toString());
    }
}
