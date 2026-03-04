// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.utils;

import io.nats.client.Connection;

import java.text.SimpleDateFormat;
import java.util.Date;

/*
    Code to help tune Consumer Create on startup
 */
public abstract class MiscUtils {
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void sleep(long millis, int nanos) {
        try {
            Thread.sleep(millis, nanos);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean waitForStatus(Connection conn, long maxWaitMs, Connection.Status statusToWaitFor) {
        long times = (maxWaitMs + 99) / 100;
        for (long x = 0; x < times; x++) {
            try {
                Thread.sleep(100);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (conn.getStatus() == statusToWaitFor) {
                return true;
            }
        }
        return false;
    }

    public static void report(Object o) {
        System.out.println(stamp() + " [INFO] " + o);
    }

    public static void reportEx(Exception e) {
        reportEx(e, null);
    }

    static final Object REXLOCK = new Object();
    public static void reportEx(Exception e, String label) {
        label = makeLabel(label);
        String stamp = stamp();
        synchronized (REXLOCK) {
            System.err.println(stamp + " [ERROR]" + label + e);
            StackTraceElement[] stack = e.getStackTrace();
            for (StackTraceElement ste : stack) {
                System.err.println(stamp + " [ERROR]" + label + ste);
            }
        }
    }

    private static String makeLabel(String label) {
        if (label == null) {
            label = " ";
        }
        else {
            label = "[" + label + "] ";
        }
        return label;
    }

    public static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private static String stamp() {
        return FORMATTER.format(new Date());
    }
}
