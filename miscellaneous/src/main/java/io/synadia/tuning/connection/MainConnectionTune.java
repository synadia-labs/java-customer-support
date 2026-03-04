// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.tuning.connection;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.impl.NoOpStatistics;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import static io.nats.client.ForceReconnectOptions.FORCE_CLOSE_INSTANCE;

public class MainConnectionTune {
    static final long NANOS_PER_MILLI = 1_000_000;

    static final String[] ServerBootstrap = new String[]{"nats://localhost:4222"};
    static final int PayloadSize = 1000;
    static final int JitterMs = 10;
    static final long ConnectionTimeoutMs = 2000; // Options.DEFAULT_CONNECTION_TIMEOUT = 2 seconds
    static final long SocketWriteTimeoutMs = 500; // Options.DEFAULT_SOCKET_WRITE_TIMEOUT = 1 minute
    static final int MaxMessagesInOutgoingQueue = 5000; // Options.DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE = 5000 [messages]
    static final int BufferSizeInBytes = 16 * 1024; // Options.DEFAULT_BUFFER_SIZE = 64k (64 * 1024)
    static final long StatisticsThresholdMillis = 1;

    @SuppressWarnings({"InfiniteLoopStatement", "BusyWait"})
    public static void main(String[] args) throws InterruptedException, IOException {
        CustomStatisticsCollector statisticsCollector = new CustomStatisticsCollector();

        Options options = Options.builder()
            .servers(ServerBootstrap)
            .connectionListener(new CustomConnectionListener())
            .connectionTimeout(ConnectionTimeoutMs)
            .socketWriteTimeout(SocketWriteTimeoutMs)
            .maxMessagesInOutgoingQueue(MaxMessagesInOutgoingQueue)
            .bufferSize(BufferSizeInBytes)
            .statisticsCollector(statisticsCollector)
            .build();

        byte[] data = new byte[PayloadSize];
        try (Connection connection = Nats.connect(options)) {
            statisticsCollector.setConnection(connection);
            while (true) {
                connection.publish("subject", data);
                Thread.sleep(ThreadLocalRandom.current().nextLong(JitterMs));
            }
        }
    }

    static class CustomConnectionListener implements ConnectionListener {
        @Override
        public void connectionEvent(Connection conn, Events type) {
            System.out.println(type.name()
                    + " | pending queue: " + conn.outgoingPendingMessageCount() + " msgs, " + conn.outgoingPendingBytes() + " bytes"
            );
        }
    }

    static class CustomStatisticsCollector extends NoOpStatistics {
        final long thresholdNanos;
        Connection connection;

        long incrementedAt;
        long inFlight = 0;
        long totalMessages = 0;
        long totalElapsedNanos = 0;
        long totalWrites = 0;
        float writeAverage;

        public CustomStatisticsCollector() {
            thresholdNanos = StatisticsThresholdMillis * NANOS_PER_MILLI;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void registerWrite(long bytes) {
            long elapsedNanos = System.nanoTime() - incrementedAt;
            if (elapsedNanos > thresholdNanos) {
                report("Threshold (" + StatisticsThresholdMillis + "ms) crossed ", elapsedNanos);
                connection.getOptions().getConnectExecutor().execute(() -> {
                    try {
                        connection.forceReconnect(FORCE_CLOSE_INSTANCE);
                    }
                    catch (IOException | InterruptedException e) {
                        // if IOException happens you should make an entirely new connection
                        // but it should actually never happen.
                        // if InterruptedException, that means your app killed this thread
                        // which also probably won't happen
                        System.exit(0);
                    }
                });
            }
            if (totalMessages % 100 == 0) {
                report("Report", elapsedNanos);
            }
            inFlight -= bytes;
            totalElapsedNanos += elapsedNanos;
            totalWrites++;
            writeAverage = (float)totalElapsedNanos / totalWrites;
        }

        @Override
        public void incrementOutBytes(long bytes) {
            totalMessages++; // I know this is called once per message
            incrementedAt = System.nanoTime();
            inFlight += bytes;
        }

        private void report(String label, long elapsedNanos) {
            float elapsedMillis = (float)elapsedNanos / 1_000_000F;
            float writeAverageMillis = writeAverage / 1_000_000F;
            System.out.println(label + ": " + inFlight + " bytes"
                + " | " + String.format("%.3f", elapsedMillis) + "ms vs average: " + String.format("%.3f", writeAverageMillis) + "ms"
                + " | pending queue: " + connection.outgoingPendingMessageCount() + " msgs, " + connection.outgoingPendingBytes() + " bytes"
            );
        }
    }
}
