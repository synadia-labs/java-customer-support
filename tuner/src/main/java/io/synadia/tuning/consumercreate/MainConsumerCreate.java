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
import io.nats.client.api.StreamConfiguration;
import io.synadia.utils.MiscUtils;
import io.synadia.utils.UniqueSubjectGenerator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.synadia.tuning.consumercreate.Report.writeCsv;
import static io.synadia.tuning.consumercreate.Report.writeTextReport;

/*
    Code to help tune Consumer Create on startup
 */
public class MainConsumerCreate {

    public static void main(String[] args) throws Exception {
        List<Report> reports = new ArrayList<>();
        Settings settings = new Settings();

        settings.optionsBuilder = () -> Options.builder().server("localhost:4222,localhost:5222,localhost:6222");

        AppStrategy[] appStrategies = new AppStrategy[] {
            AppStrategy.Client_Api_Subscribe
            , AppStrategy.Individual_Immediately
            , AppStrategy.Individual_After_Creates
            , AppStrategy.Create_Consumer_Only
        };

        SubStrategy[] subStrategies = new SubStrategy[] {
            SubStrategy.Pull_Fast_Bind
            , SubStrategy.Pull_Bind
            , SubStrategy.Pull_Provide_Stream
            , SubStrategy.Pull_Without_Stream
            , SubStrategy.Push_Without_Stream
            , SubStrategy.Push_Provide_Stream
            , SubStrategy.Push_Bind
        };

        int[] threadsPerApp = new int[]{1, 10, 100};

        for (AppStrategy asy : appStrategies) {
            for (SubStrategy ssy : subStrategies) {
                for (int tpa : threadsPerApp) {
                    settings.appStrategy = asy;
                    settings.subStrategy = ssy;
                    settings.threadsPerApp = tpa;

                    String title = tpa + " " + asy.name().toLowerCase().replace("_", " ");
                    settings.streamName = title.replace(" ", "-");
                    settings.subjectGenerator = new UniqueSubjectGenerator();
                    settings.timeoutMs = 180_000;

                    // either set the reportFrequency manually or
                    // call autoReportFrequency which makes this calculation:
                    // reportFrequency = Math.max(1, (int) (consumersPerApp / threadsPerApp * autoReportFactor));

                    // settings.reportFrequency = 10;
                    settings.autoReportFrequency();

                    if (settings.isValid()) { // just skip invalid settings when strategies don't work together.
                        Thread.sleep(1000);
                        Report r = run(title, settings);
                        if (r != null) {
                            reports.add(r);
                        }
                        cleanupAfterRun(settings);
                    }
                }
            }
        }

        writeTextReport(reports, "C:\\temp\\create-consumer-report.txt");
        writeCsv(reports, "C:\\temp\\create-consumer-report.csv");
    }

    private static void cleanupAfterRun(Settings settings) {
        if (settings.cleanupAfterRun) {
            try (Connection nc = Nats.connect(settings.optionsBuilder.getBuilder().build())) {
                JetStreamManagement jsm = nc.jetStreamManagement();
                jsm.deleteStream(settings.streamName);
            }
            catch (Exception ignore) {}
        }
    }

    public static Report run(String title, Settings settings) {
        settings.validate();

        try (Connection nc = Nats.connect(settings.optionsBuilder.getBuilder().build())) {
            if (settings.verifyConnectMs > 0) {
                if (!MiscUtils.waitForStatus(nc, settings.verifyConnectMs, Connection.Status.CONNECTED)) {
                    throw new RuntimeException("Connection not established within verify time of " + settings.verifyConnectMs + "ms");
                }
            }

            JetStreamOptions jso = JetStreamOptions.builder().requestTimeout(Duration.ofMillis(settings.timeoutMs)).build();
            JetStreamManagement jsm = nc.jetStreamManagement(jso);
            JetStream js = nc.jetStream(jso);

            // set up the stream
            try { jsm.deleteStream(settings.streamName); } catch (Exception ignore) {}
            jsm.addStream(StreamConfiguration.builder()
                .name(settings.streamName)
                .storageType(settings.storageType)
                .subjects(settings.subjectGenerator.getStreamSubject())
                .replicas(settings.replicas)
                .build());

            // start publishing - this provides load and message for subscriptions
            Publisher[] publishers = new Publisher[settings.publishInstances];
            for (int x = 0; x < settings.publishInstances; x++) {
                publishers[x] = new Publisher(settings, js, x);
                publishers[x].start();
            }
            Thread.sleep(settings.pauseAfterStartPublishingMs);

            long start = System.nanoTime();

            AppSimulator[] apps = new AppSimulator[settings.appInstances];
            for (int appId = 0; appId < settings.appInstances; appId++) {
                apps[appId] = new AppSimulator(settings, appId);
                apps[appId].start();
            }

            for (int appId = 0; appId < settings.appInstances; appId++) {
                apps[appId].join();
            }

            long elapsed = System.nanoTime() - start;

            for (Publisher p : publishers) {
                p.go.set(false);
            }
            for (Publisher p : publishers) {
                p.join();
            }

            Report r = new Report(title, settings, apps, elapsed);
            r.print(System.out);
            return r;
        }
        catch (Exception e) {
            MiscUtils.reportEx(e, "MAIN RUN EX");
            return null;
        }
    }
}
