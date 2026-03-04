// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.tuning.consumercreate;

import io.nats.client.*;

import java.time.Duration;

import static io.synadia.utils.MiscUtils.reportEx;

public class AppSimulator extends Thread {
    private final Settings settings;
    private final int appId;
    public final ConsumerAndSubscriber[] conAndSubs;

    public AppSimulator(Settings settings, int id) {
        this.settings = settings;
        this.appId = id;
        conAndSubs = new ConsumerAndSubscriber[settings.threadsPerApp];
    }

    @Override
    public void run() {

        Options options = settings.optionsBuilder.getBuilder().connectionTimeout(Duration.ofMillis(settings.timeoutMs)).build();
        try (Connection nc = Nats.connect(options)) {
            JetStreamOptions jso = JetStreamOptions.builder().requestTimeout(Duration.ofMillis(settings.timeoutMs)).build();
            JetStreamManagement jsm = nc.jetStreamManagement(jso);
            JetStream js = nc.jetStream(jso);
            Dispatcher d = nc.createDispatcher();

            int consumersEach = settings.consumersPerApp / settings.threadsPerApp;
            Thread[] threads = new Thread[settings.threadsPerApp];
            for (int tid = 0; tid < threads.length; tid++) {
                conAndSubs[tid] = new ConsumerAndSubscriber(settings, jsm, js, d, consumersEach, appId, tid);
                threads[tid] = new Thread(conAndSubs[tid]);
                threads[tid].start();
            }
            for (int i = 0; i < threads.length; i++) {
                Thread thread = threads[i];
                thread.join();
                conAndSubs[i].close();
            }
        }
        catch (Exception e) {
            reportEx(e);
        }
    }
}
