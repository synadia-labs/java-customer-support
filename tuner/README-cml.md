## Core Message Loss

This is the second version of the attempt to isolate where messages.

The program used for testing is [CoreMessageLoss](src/main/java/io/synadia/tuning/cml/CoreMessageLoss.java).
Please see the main [README](README.md) for instructions on how to run the program.

#### Results Explanation

* Buffered Messages - Messages that were moved from the queue to the byte array
* Socket Messages - Messages that were written to the socket from the byte array
* Gap Message - A gap was found in the message ids.

```
RECEIVER
  Receiver 0 Received Messages:    8,531
  Receiver 1 Received Messages:    8,424
  Receiver 2 Received Messages:    8,374
  ------------------------------ -------
  Total Received Messages:        25,329

  Received Gap Message: 25,296
  Expected Gap Message: 25,269
  Gap: 17
  Gap Bytes (Approximate): 208,896

SENDER
Before Disconnect...
  Buffered vs Socket Messages: 25,295 vs 25,290 ... 5
  Buffered vs Socket Bytes   : 311,952,129 vs 311,890,464 ... 61,665
After Disconnect...
  Buffered vs Socket Messages: 41 vs 41 ... 0
  Buffered vs Socket Bytes   : 505,653 vs 505,653 ... 0
```

### Receiver(s)
Connect to server 2 or 3 and set up a two dispatched subscription on agreed subject. 

#### Control Subscription 
The control subscription job is just to simply wait for the terminate message from the sender, so process knows when to end.

#### Main Subscription
When a message comes in...
* Extract the message id and add it to a collection for later processing.
* Count the message

#### Reporting
At the end of the run, take all messages from the collection and sort them by message id.
Then go through the entire collection seeing where there are gaps in the message id and report on the gaps.

### Sender
Connect to server 1

#### Phase 1
Normal publishing until the connection is broken. A connection is considered broken if any of these are true.
(They should all be true within milliseconds.)
* Connection status is not Connection.Status.CONNECTED
* The Connection Listener indicates `disconnected` is true
* The Error Listener indicates `readClosed` is true

#### Phase 2
Everything after the connection is broken. Let the pending message queue empty out, then send the terminate message.

#### Error Listener
An error listener is in place to log and to track notification of the underlying socket being closed (`readClosed`).
If `readClosed` is true, the sender will leave phase 1. 

#### Connection Listener
A connection listener is in place to log and track a disconnect event (`disconnected`) and the reconnect event (`reconnected`).
When `disconnected` becomes true, the sender will leave phase 1.
When `reconnected` becomes true, the sender will publish the terminate message which will be queued behind the other messages.

#### Stats Collector
In phase 1 ...

`incrementOutBytes(bytes)` and `registerWrite(bytes)` are tracked.
Every call to `incrementOutBytes` represent that 1 message and it's bytes that have been buffered from the 
pending message queue to the byte array buffer. A call to `registerWrite(bytes)` indicates that
all the bytes currently in the byte array buffer have been used to call the socket write.

Calls to `incrementOutBytes` are tracked as "buffered" until `registerWrite(bytes)` is called, at which time
"buffered" is reset. If at time of disconnect, there are values in "buffered", it means those messages/bytes
where buffered but not written.

In phase 2 ... since we have stopped publishing and will not be disconnected, we are just tracking so we can
check the total amount of messages/bytes that we published versus the total amount buffered.

#### Publishing

While the client is connected...

* Publish messages at the TPS rate. 
  * For each publish, increment an id counter and build a header entry with its value.
* Log the number of messages published during the last "publish second" each time a new "publish second" starts

Once the process becomes aware of being disconnected...
* switch all listeners to phase 2
* ensure we are reconnected
* publish the terminate message
* wait until there are no more messages in the pending queue.
