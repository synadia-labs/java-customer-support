// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.utils;

import io.nats.client.NUID;

public class UniqueSubjectGenerator implements SubjectGenerator {
    public final String subjectPrefix;
    public final String deliverPrefix;

    public UniqueSubjectGenerator() {
        this("sub-" + NUID.nextGlobalSequence() + ".", "del-");
    }

    public UniqueSubjectGenerator(String subjectPrefix, String deliverPrefix) {
        this.subjectPrefix = subjectPrefix;
        this.deliverPrefix = deliverPrefix;
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public String getStreamSubject() {
        return subjectPrefix + ">";
    }

    public String getSubject(Object id) {
        return subjectPrefix + id;
    }

    @Override
    public String getNextDeliverSubject() {
        return deliverPrefix + NUID.nextGlobal();
    }
}
