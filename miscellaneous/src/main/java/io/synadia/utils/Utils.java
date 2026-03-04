package io.synadia.utils;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsJetStreamMetaData;

import java.io.IOException;
import java.util.List;

public class Utils {
    public static StreamInfo createOrReplaceStream(Connection nc, String stream, String subject) throws IOException, JetStreamApiException {
        return createOrReplaceStream(nc.jetStreamManagement(), stream, subject);
    }

    public static StreamInfo createOrReplaceStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        try {
            jsm.deleteStream(stream);
        }
        catch (Exception ignore) {}

        return createStream(jsm, stream, subject);
    }

    public static StreamInfo cleanAndCreate(Connection nc, String stream, String subject) throws IOException, JetStreamApiException {
        return cleanAndCreate(nc.jetStreamManagement(), stream, subject);
    }

    public static StreamInfo cleanAndCreate(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        cleanupJs(jsm);
        return createStream(jsm, stream, subject);
    }

    public static StreamInfo createStream(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        StreamConfiguration sc = StreamConfiguration.builder()
            .name(stream)
            .storageType(StorageType.Memory)
            .subjects(subject)
            .build();
        return jsm.addStream(sc);
    }

    public static void cleanupJs(Connection c) throws IOException, JetStreamApiException {
        cleanupJs(c.jetStreamManagement());
    }

    public static void cleanupJs(JetStreamManagement jsm) throws IOException, JetStreamApiException {
        List<String> streams = jsm.getStreamNames();
        for (String s : streams)
        {
            try {
                jsm.deleteStream(s);
            }
            catch (Exception ignore) {}
        }
    }

    public static String stringify(Message msg) {
        NatsJetStreamMetaData meta = msg.metaData();
        return "StreamSeq: " + meta.streamSequence() + " | "
            + "ConSeq: " + meta.consumerSequence() + " | "
            + "Delivered: " + meta.deliveredCount() + " | "
            + "Pending: " + meta.pendingCount();
    }

    public static String stringify(ConsumerInfo ci) {
        return "Waiting: " + ci.getNumWaiting() + " | "
            + "Delivered: " + ci.getDelivered() + " | "
            + "Redelivered: " + ci.getRedelivered() + " | "
            + "Pending: " + ci.getNumAckPending();
    }
}
