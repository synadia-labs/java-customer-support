package io.synadia.tuning.cml;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.impl.Headers;

public abstract class CmlUtils {
    public static String MESSAGE_ID_KEY = "mid";

    public static long extractMessageId(Message msg) {
        Headers headers = msg.getHeaders();
        if (headers != null) {
            String mid = headers.getFirst(MESSAGE_ID_KEY);
            if (mid != null) {
                try {
                    return Long.parseLong(mid);
                }
                catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }

    public static String id(Connection conn) {
        return Integer.toHexString(conn.hashCode()).toUpperCase() + "/" + conn.getServerInfo().getClientId();
    }
}
