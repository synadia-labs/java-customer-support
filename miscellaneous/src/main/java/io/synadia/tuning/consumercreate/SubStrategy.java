// Copyright (c) 2021-2023 Synadia Communications Inc.  All Rights Reserved.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package io.synadia.tuning.consumercreate;

public enum SubStrategy {
    Push_Without_Stream(false),
    Push_Provide_Stream(false),
    Push_Bind(false),
    Pull_Without_Stream(true),
    Pull_Provide_Stream(true),
    Pull_Bind(true),
    Pull_Fast_Bind(true);

    public final boolean pull;

    SubStrategy(boolean pull) {
        this.pull = pull;
    }
}
