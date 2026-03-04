// Copyright (c) 2025 Synadia Communications Inc. All Rights Reserved.
// See LICENSE and NOTICE file for details.

package io.synadia.tuning.cml;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.Message;
import io.synadia.utils.Debug;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.tuning.cml.CmlUtils.id;

public class CmlErrorListener implements ErrorListener {

    public AtomicBoolean connectionException = new AtomicBoolean(false);

    private final String label;

    public CmlErrorListener(String labelSuffix) {
        this.label = "EL-" + labelSuffix;
    }

    @Override
    public void errorOccurred(final Connection conn, final String error) {
        if (error.contains("Read channel closed")) {
            connectionException.set(true);
        }
        Debug.log(label, id(conn), "errorOccurred: %s", error);
    }

    @Override
    public void exceptionOccurred(final Connection conn, final Exception exp) {
        Debug.log(label, id(conn), "exceptionOccurred: %s", exp);
        if (exp.getCause() != null) {
            Debug.log(label, "            cause:", exp.getCause());
        }
    }

    @Override
    public void slowConsumerDetected(final Connection conn, final Consumer consumer) {
        Debug.log(label, id(conn), "slowConsumerDetected: %s", consumer);
    }

    @Override
    public void messageDiscarded(final Connection conn, final Message msg) {
        Debug.log(label, id(conn), "messageDiscarded: %s" + msg);
    }

    @Override
    public void socketWriteTimeout(Connection conn) {
        connectionException.set(true);
        Debug.log(label, id(conn), "socketWriteTimeout");
    }
}
