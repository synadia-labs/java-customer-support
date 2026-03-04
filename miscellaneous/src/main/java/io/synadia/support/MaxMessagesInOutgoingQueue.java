package io.synadia.support;

import io.nats.client.*;
import io.nats.client.impl.ErrorListenerConsoleImpl;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.utils.Utils.createOrReplaceStream;

public class MaxMessagesInOutgoingQueue {
    public static void main(String[] args) {
        Options options = new Options.Builder()
            .server("nats://localhost:4222")
            .connectionListener((conn, type) -> System.out.println("CL: " + type))
            .errorListener(new ErrorListenerConsoleImpl())
            .maxMessagesInOutgoingQueue(20)
            .build();

        try (Connection nc = Nats.connect(options)) {
            String stream = "mmioq";
            String subject = "z";
            JetStreamManagement jsm = nc.jetStreamManagement();
            createOrReplaceStream(jsm, stream, subject);

            Thread[] threads = new Thread[10];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> publish(nc, subject));
                threads[i].start();
            }
            publish(nc, subject);

            for (Thread thread : threads) {
                thread.join();
            }

            Thread.sleep(1000);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int TIX = 0;
    private static final AtomicBoolean KEEP_RUNNING = new AtomicBoolean(true);

    private static void publish(Connection nc, String subject) {
        int tix = ++TIX;
        try {
            for (int x = 0; KEEP_RUNNING.get() && x < 1_000_000; x++) {
                nc.publish(subject, null);
            }
        }
        catch (Exception e) {
            KEEP_RUNNING.set(false);
            System.out.println("Thread " + tix + " : " + e);
        }
    }
}
