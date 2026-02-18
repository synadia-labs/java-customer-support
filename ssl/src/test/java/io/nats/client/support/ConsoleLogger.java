// Copyright 2025 The NATS Authors
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

package io.nats.client.support;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * A pre-configured {@link Logger} that writes formatted output to {@code System.out}.
 * <p>
 * Unlike the default JUL {@link ConsoleHandler} which writes to {@code System.err},
 * this logger writes all output to {@code System.out} with a concise timestamp format.
 * <p>
 * Usage:
 * <pre>
 *   Logger log = ConsoleLogger.getLogger("MyComponent");
 *   log.info("Connected to server");
 *   log.warning("Reconnect attempt failed");
 * </pre>
 */
public class ConsoleLogger {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ConsoleLogger() {} /* ensures cannot be constructed */

    /**
     * Returns a Logger configured to write to {@code System.out} with a concise format.
     * The logger's level is set to {@link Level#ALL}; filter by adjusting the returned logger's level.
     *
     * @param name the logger name, typically a class name
     * @return a configured Logger instance
     */
    public static Logger getLogger(String name) {
        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);

        // avoid adding duplicate handlers if called more than once with the same name
        for (Handler h : logger.getHandlers()) {
            if (h instanceof StdOutHandler) {
                return logger;
            }
        }

        StdOutHandler handler = new StdOutHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new ConsoleFormatter());
        logger.addHandler(handler);
        return logger;
    }

    /**
     * Returns a Logger configured to write to {@code System.out} using the class's fully qualified name.
     *
     * @param clazz the class to derive the logger name from
     * @return a configured Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * A handler that writes log records to {@code System.out} instead of {@code System.err}.
     */
    static class StdOutHandler extends StreamHandler {
        StdOutHandler() {
            setOutputStream(System.out);
        }

        @Override
        public synchronized void publish(LogRecord record) {
            super.publish(record);
            flush();
        }

        @Override
        public synchronized void close() throws SecurityException {
            flush();
            // do not close System.out
        }
    }

    /**
     * A compact single-line formatter: {@code timestamp level [loggerName] message}
     */
    static class ConsoleFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(TIMESTAMP_FORMAT.format(ZonedDateTime.now()));
            sb.append(' ');
            sb.append(padLevel(record.getLevel()));
            sb.append(" [");
            sb.append(shortenName(record.getLoggerName()));
            sb.append("] ");
            sb.append(formatMessage(record));
            sb.append('\n');

            if (record.getThrown() != null) {
                StringWriter sw = new StringWriter();
                record.getThrown().printStackTrace(new PrintWriter(sw));
                sb.append(sw);
            }

            return sb.toString();
        }

        private static String padLevel(Level level) {
            StringBuilder name = new StringBuilder(level.getName());
            // pad to 7 chars to align output (WARNING is the longest standard level)
            while (name.length() < 7) {
                name.append(" ");
            }
            return name.toString();
        }

        private static String shortenName(String name) {
            if (name == null) {
                return "?";
            }
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        }
    }
}
