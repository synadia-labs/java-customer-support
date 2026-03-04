// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.tuning.consumercreate;

import io.nats.client.JetStream;
import io.synadia.utils.MiscUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class Publisher extends Thread {
    private final Settings settings;
    private final JetStream js;
    private final int id;
    private final String payloadTemplate;

    public AtomicBoolean go = new AtomicBoolean(true);
    public AtomicLong totalTime = new AtomicLong();
    public AtomicLong count = new AtomicLong();

    public Publisher(Settings settings, JetStream js, int id) {
        this.js = js;
        this.id = id;
        StringBuilder sb = new StringBuilder();
        this.settings = settings;
        for (int x = 0; x < settings.payloadSize; x++) {
            sb.append(" ");
        }
        payloadTemplate = sb.toString();
    }

    @Override
    public void run() {
        while (go.get()) {
            try {
                String subject = settings.subjectGenerator.getSubject(id);
                byte[] payload = (id + "" + (count.incrementAndGet()) + payloadTemplate).substring(0, settings.payloadSize).getBytes();
                long start = System.nanoTime();
                js.publish(subject, payload);
                totalTime.set(totalTime.get() + System.nanoTime() - start);
            }
            catch (Exception e) {
                MiscUtils.reportEx(e, "PUB " + id);
            }
        }
    }
}
