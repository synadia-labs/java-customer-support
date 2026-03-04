package io.synadia.tuning.cml;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NoOpStatistics;
import io.synadia.utils.PropertyUtils;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.tuning.cml.CmlUtils.MESSAGE_ID_KEY;
import static io.synadia.tuning.cml.CmlUtils.extractMessageId;
import static io.synadia.utils.ArgumentUtils.*;
import static io.synadia.utils.Debug.log;
import static io.synadia.utils.Debug.stringify;
import static io.synadia.utils.MiscUtils.sleep;

public class CoreMessageLoss {
    // Labels
    private static final String TPS_SENDER = "SENDER";
    private static final String TPS_RECEIVER = "RECEIVER";

    // Run values
    private static final String TEST_SUBJECT = "test";
    private static final String TERMINATE_SUBJECT = "term";
    private static final String TEST_QUEUE = "q";
    private static final long WAIT_FOR_MESSAGES = 5000;

    private static final String KEY_PROPS = "props";
    private static final String[] KEYS_SERVERS = new String[]{"servers", "s"};
    private static final String[] KEYS_TPS = new String[]{"tps", "t"};
    private static final String[] KEYS_PAYLOAD_SIZE = new String[]{"payload.size", "p"};
    private static final String[] KEYS_RECEIVERS = new String[]{"receivers", "r"};
    private static final String[] KEYS_SEND_BUFFER_SIZE = new String[]{"send.buffer.size", "b"};
    private static final String[] KEYS_CONNECTION_TIMEOUT_MILLIS = new String[]{"connection.timeout.millis", "c"};

    // arguments
    final String[] servers;
    final int targetTps;
    final int payloadSize;
    final int numReceivers;
    final int sendBufferSize;
    final int maxMessagesInOutgoingQueue;
    final long connectionTimeoutMillis;

    // per run
    ScheduledExecutorService scheduler;

    public static void main(String[] args) throws Exception {
        new CoreMessageLoss(args).run();
    }

    public CoreMessageLoss(String[] args) throws IOException {
        // props will come from cml.application.properties unless props=<> is on the command line
        String propsFile = getArg(args, "cml.application.properties", KEY_PROPS);
        Properties props = PropertyUtils.loadProperties(propsFile);

        String _servers = getProperty(props, "nats://localhost:4222,nats://localhost:5222,nats://localhost:6222", KEYS_SERVERS[0]);
        int _targetTps = getIntProperty(props, 10_000, KEYS_TPS[0]);
        int _payloadSize = getIntProperty(props, 12 * 1024, KEYS_PAYLOAD_SIZE[0]);
        int _numReceivers = getIntProperty(props, 1, KEYS_RECEIVERS[0]);
        int _sendBufferSize = getIntProperty(props, -1, KEYS_SEND_BUFFER_SIZE[0]);
        long _connectionTimeoutMillis = getLongProperty(props, 5000, KEYS_CONNECTION_TIMEOUT_MILLIS[0]);

        // command line takes precedent if present
        _servers = getArg(args, _servers, KEYS_SERVERS);
        _targetTps = getIntArg(args, _targetTps, KEYS_TPS);
        _payloadSize = getIntArg(args, _payloadSize, KEYS_PAYLOAD_SIZE);
        _numReceivers = getIntArg(args, _numReceivers, KEYS_RECEIVERS);
        _sendBufferSize = getIntArg(args, _sendBufferSize, KEYS_SEND_BUFFER_SIZE);
        _connectionTimeoutMillis = getLongArg(args, _connectionTimeoutMillis, KEYS_CONNECTION_TIMEOUT_MILLIS);

        //noinspection DataFlowIssue
        servers = _servers.split(",");
        targetTps = _targetTps;
        payloadSize = _payloadSize;
        numReceivers = _numReceivers;
        sendBufferSize = _sendBufferSize;
        connectionTimeoutMillis = _connectionTimeoutMillis;
        int mmiq = targetTps * 125 / 100; // 125 % of target tps
        maxMessagesInOutgoingQueue = Math.max(mmiq, Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE);

        log("TPS", "----- Application Options -----");
        log("TPS", "Servers", servers);
        log("TPS", "Target TPS", targetTps);
        log("TPS", "Payload Size", payloadSize);
        log("TPS", "Num Receivers", numReceivers);
        log("TPS", "Send Buffer Size", sendBufferSize);
        log("TPS", "Max Messages In Outgoing Queue", maxMessagesInOutgoingQueue);
        log("TPS", "Connection Timeout Millis", connectionTimeoutMillis);

        reportSocketBufferSize();
    }

    public void run() throws InterruptedException {
        scheduler = Executors.newScheduledThreadPool(1);
        try {

            for (int ix = 0; ix < numReceivers; ix++) {
                receivers.add(new Receiver());
            }

            // Receiver threads
            List<Thread> threads = new ArrayList<>();
            for (int rx = 0; rx < numReceivers; rx++) {
                int finalRx = rx;
                Thread r = new Thread(() -> {
                    try {
                        receive(finalRx);
                    }
                    catch (Exception ignored) {
                    }
                });
                r.setName("R-" + rx + "-main");
                r.start();
                threads.add(r);
            }
            for (int rx = 0; rx < numReceivers; rx++) {
                AtomicBoolean ready = receivers.get(rx).ready;
                while (!ready.get()) {
                    sleep(10);
                }
            }

            // Receivers periodic reporting
            scheduler.scheduleAtFixedRate(
                () -> {
                    long receivedMessages = 0;
                    for (int ix = 0; ix < numReceivers; ix++) {
                        long rm = receivers.get(ix).receivedMessages;
                        receivedMessages += rm;
                    }
                    log(TPS_RECEIVER, "Total Received Messages: %s", receivedMessages, "Highest Message Id Received: %s", highestMessageId.get());
                    if (System.currentTimeMillis() - lastReceive.get() > WAIT_FOR_MESSAGES) {
                        log(TPS_RECEIVER, "RECEIVER TIMEOUT: %s", System.currentTimeMillis() - lastReceive.get());
                        for (Receiver r : receivers) {
                            r.done.set(true);
                        }
                    }
                },
                2500, 2500, TimeUnit.MILLISECONDS);

            scheduler.scheduleAtFixedRate(
                () -> {
                    long receivedMessages = 0;
                    for (int ix = 0; ix < numReceivers; ix++) {
                        long rm = receivers.get(ix).receivedMessages;
                        receivedMessages += rm;
                    }
                    log(TPS_RECEIVER, "Total Received Messages: %s", receivedMessages);
                },
                1, 1, TimeUnit.SECONDS);

            // Sender thread
            Thread s = new Thread(() -> {
                try {
                    send();
                }
                catch (Exception ignored) {
                }
            });
            s.setName("S-main");
            s.start();

            // wait for all the threads to finish
            s.join();
            for (Thread t : threads) {
                t.join();
            }

            sleep(100); // give callbacks time to finish

            reportSocketBufferSize();
            reportReceivers();
            reportSenders();
        }
        finally {
            if (!scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        }
    }

    private void reportReceivers() {
        List<Long> receivedMessageIds = new ArrayList<>();
        messageIdsTrackingQueue.drainTo(receivedMessageIds);
        receivedMessageIds.sort(Long::compareTo);

        System.out.println("\n" + TPS_RECEIVER);
        long receivedMessages = 0;
        for (int ix = 0; ix < numReceivers; ix++) {
            long rm = receivers.get(ix).receivedMessages;
            receivedMessages += rm;
            System.out.println(stringify("  Receiver %s Received Messages:  %s", ix, formatRight(rm, 7)));
        }
        System.out.println("  ------------------------------ -------");
        System.out.println(stringify("  Total Received Messages:       %s", formatRight(receivedMessages, 7)));

        long expected = receivedMessageIds.get(0);
        for (Long mid : receivedMessageIds) {
            if (mid != expected) {
                System.out.println(stringify("\n  Received Gap Message: %s", format(mid)));
                System.out.println(stringify("  Expected Gap Message: %s", format(expected)));
                long diff = mid - expected;
                System.out.println(stringify("  Gap: %s", diff));
                System.out.println(stringify("  Gap Bytes (Approximate): %s", format(diff * payloadSize)));
            }
            expected = mid + 1;
        }
    }

    private void reportSenders() {
        // ----------------------------------------------------------------------------------------------------
        // Report Sender
        // ----------------------------------------------------------------------------------------------------
        System.out.println("\n" + TPS_SENDER);
        System.out.println("Before Disconnect...");
        printSendResultAndDiff("Buffered vs Socket Messages",
            sendStats.pay.bufferedMessages, sendStats.pay.writtenMessages);
        printSendResultAndDiff("Buffered vs Socket Bytes   ",
            sendStats.pay.bufferedBytes, sendStats.pay.writtenBytes);

        System.out.println("After Disconnect...");
        printSendResultAndDiff("Buffered vs Socket Messages",
            sendStats.pay2.bufferedMessages, sendStats.pay2.writtenMessages);
        printSendResultAndDiff("Buffered vs Socket Bytes   ",
            sendStats.pay2.bufferedBytes, sendStats.pay2.writtenBytes);
    }

    private void printSendResult(String s, Number n) {
        System.out.println(stringify("  " + s + ": %s", format(n)));
    }

    private void printSendResultAndDiff(String s, Number n1, Number n2) {
        long diff = n1.longValue() - n2.longValue();
        System.out.println(stringify("  " + s + ": %s vs %s ... %s", format(n1), format(n2), format(diff)));
    }

    // ----------------------------------------------------------------------------------------------------
    // Sender
    // ----------------------------------------------------------------------------------------------------
    AtomicLong pubId;
    CmlStatsCollector sendStats;
    CmlConnectionListener sendCL;
    CmlErrorListener sendEL;

    private void send() throws IOException, InterruptedException {
        pubId = new AtomicLong(0);
        sendStats = new CmlStatsCollector(payloadSize);

        sendCL = new CmlConnectionListener(TPS_SENDER, servers, false);
        sendEL = new CmlErrorListener(TPS_SENDER);

        Options options  = new Options.Builder()
            .servers(servers)
            .ignoreDiscoveredServers()
            .noRandomize()
            .connectionTimeout(connectionTimeoutMillis)
            .sendBufferSize(sendBufferSize)
            .maxMessagesInOutgoingQueue(maxMessagesInOutgoingQueue)
            .statisticsCollector(sendStats)
            .connectionListener(sendCL)
            .errorListener(sendEL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            byte[] payload = new byte[payloadSize];
            Headers h = new Headers();

            long currentSecond = -1;
            long messagesThisSecond = 0;
            long nextSecondStart = -1;
            long startNanos = System.nanoTime();

            while (nc.getStatus() == Connection.Status.CONNECTED
                && !sendEL.connectionException.get() && !sendCL.disconnected.get())
            {
                // Check if we've moved to a new second
                long now = System.nanoTime();
                if (now >= nextSecondStart) {
                    if (messagesThisSecond > 0) {
                        log(TPS_SENDER, "Messages Last Second: " + messagesThisSecond);
                    }
                    currentSecond++;
                    messagesThisSecond = 0;
                    nextSecondStart = startNanos + ((currentSecond + 1) * 1_000_000_000L);
                }

                // Only send if we haven't hit the target for this second
                if (messagesThisSecond < targetTps) {
                    try {
                        h.put(MESSAGE_ID_KEY, pubId.incrementAndGet() + "");
                        nc.publish(TEST_SUBJECT, h, payload);
                        messagesThisSecond++;

                        // Calculate sleep time to maintain even distribution
                        long remainingInSecond = nextSecondStart - System.nanoTime();
                        long remainingMessages = targetTps - messagesThisSecond;
                        if (remainingMessages > 0 && remainingInSecond > 0) {
                            long sleepTime = remainingInSecond / (remainingMessages + 1);
                            if (sleepTime > 100_000) { // Only sleep if more than 100 microseconds
                                sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                            }
                        }
                    }
                    catch (Exception e) {
                        log(TPS_SENDER, "Error sending message id %s during test: %s", pubId.get(), e.getMessage());
                        pubId.decrementAndGet();
                    }
                }
                else {
                    // Wait for next second if we've hit the target for this second
                    long sleepTime = nextSecondStart - System.nanoTime();
                    if (sleepTime > 0) {
                        sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
                    }
                }
            }

            sendStats.pay.debug(TPS_SENDER, "Before Disconnect Payloads");

            sendStats.startPhase2();

            while (!sendCL.reconnected.get()) {
                log(TPS_SENDER, "Waiting for Reconnect");
                sleep(10);
            }

            log(TPS_SENDER, "Publishing Control Terminate Message");
            nc.publish(TERMINATE_SUBJECT, null);


            long wait = 30000;
            while (wait > 0) {
                sleep(100);
                int notDone = receivers.size();
                for (Receiver r : receivers) {
                    if (r.done.get()) {
                        notDone--;
                    }
                }
                if (notDone == 0) {
                    break;
                }
                wait -= 100;
            }
            log(TPS_SENDER, "Done");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Receiver
    // ----------------------------------------------------------------------------------------------------
    static class Receiver {
        long receivedMessages;
        CmlConnectionListener receiveCL;
        CmlErrorListener receiveEL;
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicBoolean done = new AtomicBoolean(false);
    }

    AtomicLong highestMessageId = new AtomicLong(0);
    AtomicLong lastReceive = new AtomicLong(System.currentTimeMillis());
    LinkedBlockingQueue<Long> messageIdsTrackingQueue = new LinkedBlockingQueue<>();
    List<Receiver> receivers = new ArrayList<>();

    private void receive(int rx) throws IOException, InterruptedException {
        Receiver r = receivers.get(rx);
        receivers.add(r);

        String label = TPS_RECEIVER + "-" + rx;
        r.receiveCL = new CmlConnectionListener(label, servers, true);
        r.receiveEL = new CmlErrorListener(label);

        Options options  = new Options.Builder()
            .server(servers[rx % 2 == 0 ? 2 : 1])
            .ignoreDiscoveredServers()
            .connectionTimeout(connectionTimeoutMillis)
            .statisticsCollector(new NoOpStatistics())
            .connectionListener(r.receiveCL)
            .errorListener(r.receiveEL)
            .build();

        try (Connection nc = Nats.connect(options)) {
            Dispatcher d = nc.createDispatcher();

            d.subscribe(TEST_SUBJECT, TEST_QUEUE, msg -> {
                long mid = extractMessageId(msg);
                messageIdsTrackingQueue.add(mid);
                highestMessageId.set(Math.max(highestMessageId.get(), mid));
                lastReceive.set(System.currentTimeMillis());
                if (++r.receivedMessages == 1) {
                    log(label, "Started Receiving");
                }
            });

            d.subscribe(TERMINATE_SUBJECT, msg -> {
                log(label, "Received Control - Terminate Message.");
                r.done.set(true);
            });

            sleep(50);
            r.ready.set(true);
            log(label, "READY");


            long wait = 30000;
            while (wait > 0) {
                sleep(100);
                if (r.done.get()) {
                    break;
                }
                wait -= 100;
            }
            log(label, "Done");
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    private void reportSocketBufferSize() {
        try {
            Socket socket = new Socket();
            log("TPS", "Socket Receive Buffer: %s bytes", socket.getReceiveBufferSize());
            log("TPS", "Socket Send Buffer: %s bytes", socket.getSendBufferSize());
            socket.close();
        }
        catch (IOException ioe) {
            log("TPS", "Exception Reporting Socket Buffer Sizes", ioe);
        }
    }
}
