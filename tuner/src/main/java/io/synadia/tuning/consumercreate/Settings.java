// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.tuning.consumercreate;

import io.nats.client.Options;
import io.nats.client.api.StorageType;
import io.synadia.utils.OptionsBuilderFactory;
import io.synadia.utils.SubjectGenerator;
import io.synadia.utils.UniqueSubjectGenerator;

public class Settings {

    public OptionsBuilderFactory optionsBuilder = () ->
        Options.builder().server("localhost:4222,localhost:5222,localhost:6222");

    public long verifyConnectMs = 10000; // < 0 means do not verify

    public String streamName = "stream";
    public StorageType storageType = StorageType.File;
    public int replicas = 3;

    public SubjectGenerator subjectGenerator = new UniqueSubjectGenerator();

    public long timeoutMs = 20_000; // 20 seconds

    public int appInstances = 10;
    public int threadsPerApp = 10;
    public int consumersPerApp = 100;
    public long beforeCreateDelayMs = 20;
    public long beforeSubDelayMs = 20;

    public long inactiveThresholdMs = timeoutMs * 2;
    public AppStrategy appStrategy = AppStrategy.Client_Api_Subscribe;
    public SubStrategy subStrategy = SubStrategy.Push_Provide_Stream;

    public int publishInstances = 10;
    public int payloadSize = 100;
    public int pauseAfterStartPublishingMs = 2000;

    public int reportFrequency = 1;
    public float autoReportFactor = 0.2f;

    public boolean reportNanos = false;
    public boolean cleanupAfterRun = true;

    public long time(long t) {
        return reportNanos ? t : t / 1_000_000;
    }

    public String timeLabel() {
        return reportNanos ? "ns" : "ms";
    }

    public void autoReportFrequency() {
        reportFrequency = Math.max(1, (int) (consumersPerApp / threadsPerApp * autoReportFactor));
    }

    public void validate() {
        if (!isValid()) {
            System.err.println(subStrategy.name().replace("_", " ") + " not allowed for " + appStrategy.name().replace("_", " "));
            System.exit(-1);
        }
    }
    public boolean isValid() {
        if (appStrategy == AppStrategy.Client_Api_Subscribe) {
            if (subStrategy == SubStrategy.Push_Bind
                || subStrategy == SubStrategy.Pull_Bind
                || subStrategy == SubStrategy.Pull_Fast_Bind
            ) {
                return false;
            }
        }
        return true;
    }
}
