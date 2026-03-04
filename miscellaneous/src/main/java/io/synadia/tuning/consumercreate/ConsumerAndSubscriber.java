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
import io.nats.client.api.ConsumerConfiguration;
import io.synadia.utils.MiscUtils;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.utils.MiscUtils.sleep;

public class ConsumerAndSubscriber implements Runnable {
    Settings settings;
    JetStreamManagement jsm;
    JetStream js;
    Dispatcher d;
    int consumersEach;
    int appId;
    int threadId;
    public JetStreamSubscription[] subs;
    public long[] createTime;
    public long[] subscribeTime;

    public ConsumerAndSubscriber(Settings settings, JetStreamManagement jsm, JetStream js, Dispatcher d, int consumersEach, int appId, int threadId) {
        this.settings = settings;
        this.jsm = jsm;
        this.js = js;
        this.d = d;
        this.consumersEach = consumersEach;
        subs = new JetStreamSubscription[consumersEach];
        createTime = new long[consumersEach];
        subscribeTime = new long[consumersEach];
        this.appId = appId;
        this.threadId = threadId;

        for (int conIx = 0; conIx < consumersEach; conIx++) {
            createTime[conIx] = Long.MIN_VALUE;
            subscribeTime[conIx] = Long.MIN_VALUE;
        }
    }

    public void close() {
        for (JetStreamSubscription sub : subs) {
            try {
                sub.unsubscribe();
            }
            catch (Exception ignore) {
            }
        }
    }

    @Override
    public void run() {
        switch (settings.appStrategy) {
            case Individual_Immediately:
                for (int conIx = 0; conIx < consumersEach; conIx++) {
                    String name = getName(conIx);
                    subscribeBind(conIx, createConsumer(conIx, name));
                }
                break;

            case Individual_After_Creates:
                List<ConsumerConfiguration> ccs = new ArrayList<>();
                for (int conIx = 0; conIx < consumersEach; conIx++) {
                    ccs.add(createConsumer(conIx, getName(conIx)));
                }
                for (int conIx = 0; conIx < consumersEach; conIx++) {
                    subscribeBind(conIx, ccs.get(conIx));
                }
                break;

            case Client_Api_Subscribe:
                for (int conIx = 0; conIx < consumersEach; conIx++) {
                    apiSubscribe(conIx, getName(conIx));
                }
                break;

            case Create_Consumer_Only:
                for (int conIx = 0; conIx < consumersEach; conIx++) {
                    createConsumer(conIx, getName(conIx));
                }
                break;
        }
    }

    private ConsumerConfiguration createConsumer(int conIx, String name) {
        try {
            sleep(settings.beforeCreateDelayMs);
            long start = System.nanoTime();
            ConsumerConfiguration cc = createConsumerConfiguration(name, conIx, settings.subStrategy.pull);
            jsm.addOrUpdateConsumer(settings.streamName, cc);
            createTime[conIx] = System.nanoTime() - start;
            if (conIx == 0 || conIx % settings.reportFrequency == 0) {
                MiscUtils.report("Create Consumer | " + name + " | " + settings.time(createTime[conIx]) + settings.timeLabel());
            }
            return cc;
        }
        catch (Exception e) {
            MiscUtils.reportEx(e, "Create Consumer Exception " + name);
            return null;
        }
    }

    private ConsumerConfiguration createConsumerConfiguration(String name, int conIx, boolean pull) {
        return ConsumerConfiguration.builder()
            .name(name)
            .filterSubject(settings.subjectGenerator.getSubject(conIx))
            .inactiveThreshold(settings.inactiveThresholdMs)
            .deliverSubject(pull ? null : settings.subjectGenerator.getNextDeliverSubject())
            .build();
    }

    private void apiSubscribe(int conIx, String name) {
        try {
        sleep(settings.beforeCreateDelayMs);
            ConsumerConfiguration cc = createConsumerConfiguration(null, conIx, settings.subStrategy.pull);
            long start = System.nanoTime();
            switch (settings.subStrategy) {
                case Push_Without_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(), d, Message::ack, false,
                        PushSubscribeOptions.builder().configuration(cc).build());
                    break;
                case Push_Provide_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(), d, Message::ack, false,
                        PushSubscribeOptions.builder().configuration(cc).stream(settings.streamName).build());
                    break;
                case Pull_Without_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(),
                        PullSubscribeOptions.builder().configuration(cc).build());
                    break;
                case Pull_Provide_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(),
                        PullSubscribeOptions.builder().configuration(cc).stream(settings.streamName).build());
                    break;
            }
            subscribeTime[conIx] = System.nanoTime() - start;
            if (conIx == 0 || conIx % settings.reportFrequency == 0) {
                System.out.println("-------------> " + name + " " + subs + " " + subs[conIx]);
                if (name.equals(subs[conIx].getConsumerName())) {
                    MiscUtils.report("Subscribe | " + name + " | " + settings.time(subscribeTime[conIx]) + settings.timeLabel());
                }
                else {
                    MiscUtils.report("Subscribe | " + name + " / " + subs[conIx].getConsumerName() + " | " + settings.time(subscribeTime[conIx]) + settings.timeLabel());
                }
            }
        }
        catch (Exception e) {
            MiscUtils.reportEx(e, "Subscribe Exception " + name);
            subscribeTime[conIx] = -1;
        }
    }

    private void subscribeBind(int conIx, ConsumerConfiguration cc) {
        if (cc == null || createTime[conIx] == -1) { // create failed, can't subscribe
            return;
        }

        try {
            sleep(settings.beforeSubDelayMs);
            long start = System.nanoTime();
            switch (settings.subStrategy) {
                case Push_Without_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(), d, Message::ack, false,
                        PushSubscribeOptions.builder().configuration(cc).build());
                    break;
                case Push_Provide_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(), d, Message::ack, false,
                        PushSubscribeOptions.builder().stream(settings.streamName).configuration(cc).build());
                    break;
                case Pull_Without_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(),
                        PullSubscribeOptions.builder().configuration(cc).build());
                    break;
                case Pull_Provide_Stream:
                    subs[conIx] = js.subscribe(cc.getFilterSubject(),
                        PullSubscribeOptions.builder().stream(settings.streamName).configuration(cc).build());
                    break;
                case Pull_Bind:
                    subs[conIx] = js.subscribe(null, PullSubscribeOptions.bind(settings.streamName, cc.getName()));
                    break;
                case Pull_Fast_Bind:
                    subs[conIx] = js.subscribe(null, PullSubscribeOptions.fastBind(settings.streamName, cc.getName()));
                    break;
            }
            subscribeTime[conIx] = System.nanoTime() - start;
            if (conIx == 0 || conIx % settings.reportFrequency == 0) {
                MiscUtils.report("SUB " + cc.getName() + " | " + settings.time(subscribeTime[conIx]) + settings.timeLabel());
            }
        }
        catch (Exception e) {
            MiscUtils.reportEx(e, "SUB EX " + cc.getName());
        }
    }

    private String getName(int conIx) {
        return "App" + appId + "-Thread" + threadId + "-Id" + conIx;
    }
}
