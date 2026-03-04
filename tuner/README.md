# Synadia Java Application Tuner

This repository contains customizable code to help you tune your system.

Gradle can be used to build the project and run examples from the command line. 
The Gradle version with this project is 6.8.3, and the uber jar can be built like so: 

```
gradlew clean uberJar 
```

### Core Message Loss

Demonstrate how messages can be lost during server or network outages.

#### Basic steps 
- Run a 3 server cluster.
- Run the program.
  - The receivers connect to either the 3rd or 2nd server in a round-robin fashion
  - The sender initially connects to the 1st server, then after disconnect connects to the 2nd server
- Wait a couple seconds then block or stop the 1st server. This causes the sender to disconnect and reconnect. Unblock or restart after 1 second.
- Review results.

### Command line run
```
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.cml.CoreMessageLoss
```

#### Configuration
The program will use the `cml.application.properties`

```
servers=nats://localhost:4222,nats://localhost:5222,nats://localhost:6222
tps=10k
payload.size=12ki
receivers=3
send.buffer.size=64ki
connection.timeout.millis=5000
```

You can also supply a different property file on the command line:
```
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.cml.CoreMessageLoss "props=path/to/my/app.properties"
```

Or you can just supply the properties directly on the command line. Command line parameters take precedence over the properties file. 
Numbers can be fully written out or can use the "k" for times 1000 of "ki" for times 1024. 10k = 10000, 10ki = 10240

All parameters except `props` have short and long names
* `props`
* `servers` or `s` 
* `tps` or `t` 
* `payload.size` or `p` 
* `receivers` or `r` 
* `send.buffer.size` or `b` 
* `connection.timeout.millis` or `c`

```
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.cml.CoreMessageLoss tps=10k receivers=3 payload.size=8ki send.buffer.size=32ki
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.cml.CoreMessageLoss t=10k r=3 p=8ki b=32ki
```

### Connection Tuning

Small application that can help tune the connection in regard to the publish/write side. To run:

```
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.connection.MainConnectionTune
```

### Subscription and Consumer

Currently, when starting up a large number of ephemeral consumers when your app starts up
as large number of these may take some time to complete, depending on your parallelization and volume.

To run:

```
java -cp build/libs/tuning-1.0.0-uber.jar io.synadia.tuning.consumercreate.MainConsumerCreate
```
___

Copyright (c) 2021-2025 Synadia Communications Inc.  All Rights Reserved.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
