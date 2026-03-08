I have verified this on two different physical Windows machines.

* My build nats-server scripts have been working fine for years.
* A regular, single instance works fine with both versions. 
* Running a cluster works against `v2.12.4` but not against `v2.14.0-dev`
* This appears to be a Windows specific issue, I found this with a failing unit test that runs fine on CI under ubuntu-latest
* I have ruled out a "my machine" permissions error, verified by 
  * identical behavior on multiple machines 
  * the old version works
  * it worked when the older version was the head

### go version

Machine 1 `go version go1.25.0 windows/amd64`

Machine 2 `go version go1.26.0 windows/amd64`

### Server Build Scripts
* Build the head: [newnats.bat](newnats.bat) 
* Build from a tag [newtag.bat](newtag.bat)

### Cluster Configs
* [server1.conf](server1.conf) Server 1
* [server2.conf](server2.conf) Server 2
* [server3.conf](server3.conf) Server 3

Each conf specifies a different `store_dir` to properly simulate different machines

I run the cluster. (`start` just starts each in a new console)
```
start nats-server -js -c C:\tmp\server1.conf
start nats-server -js -c C:\tmp\server2.conf
start nats-server -js -c C:\tmp\server3.conf
```

## Problem

When I build `v2.12.4` (via `newtag v2.12.4`) and then run the cluster, everything works fine. 
When I build `v2.14.0-dev` (via `newnats`) and then run the cluster I get:
```
[FTL] Can't start JetStream: sync C:\tmp\jetstream\4222\jetstream\$SYS\_js_\_meta_: Access is denied.
```

```
[21760] 2026/03/08 10:09:01.747227 [INF] Starting nats-server
[21760] 2026/03/08 10:09:01.747227 [INF]   Version:  2.14.0-dev
[21760] 2026/03/08 10:09:01.747227 [INF]   Git:      [not set]
[21760] 2026/03/08 10:09:01.747773 [INF]   Cluster:  cluster
[21760] 2026/03/08 10:09:01.747773 [INF]   Name:     server1
[21760] 2026/03/08 10:09:01.747773 [INF]   Node:     1tDAyReq
[21760] 2026/03/08 10:09:01.747773 [INF]   ID:       NDXERLJH3NJJQAXB2TPGYIICLG2JKLQMBEVHTB6AUQ2INXV3N2WLMDLF
[21760] 2026/03/08 10:09:01.747773 [INF] Using configuration file: C:\tmp\server1.conf (sha256:77387bd74d32b4d3356076a71c9ab99b9a7e5bd30931d660706fa6cac2617138)
[21760] 2026/03/08 10:09:01.748921 [INF] Starting http monitor on 0.0.0.0:4280
[21760] 2026/03/08 10:09:01.749700 [INF] Starting JetStream
[21760] 2026/03/08 10:09:01.750685 [INF]     _ ___ _____ ___ _____ ___ ___   _   __  __
[21760] 2026/03/08 10:09:01.750685 [INF]  _ | | __|_   _/ __|_   _| _ \ __| /_\ |  \/  |
[21760] 2026/03/08 10:09:01.751190 [INF] | || | _|  | | \__ \ | | |   / _| / _ \| |\/| |
[21760] 2026/03/08 10:09:01.751190 [INF]  \__/|___| |_| |___/ |_| |_|_\___/_/ \_\_|  |_|
[21760] 2026/03/08 10:09:01.751190 [INF]
[21760] 2026/03/08 10:09:01.751190 [INF]          https://docs.nats.io/jetstream
[21760] 2026/03/08 10:09:01.751190 [INF]
[21760] 2026/03/08 10:09:01.751190 [INF] ---------------- JETSTREAM ----------------
[21760] 2026/03/08 10:09:01.751190 [INF]   Strict:          true
[21760] 2026/03/08 10:09:01.751190 [INF]   Max Memory:      47.57 GB
[21760] 2026/03/08 10:09:01.751190 [INF]   Max Storage:     1.00 TB
[21760] 2026/03/08 10:09:01.751190 [INF]   Store Directory: "C:\tmp\jetstream\4222\jetstream"
[21760] 2026/03/08 10:09:01.751190 [INF]   API Level:       4
[21760] 2026/03/08 10:09:01.751190 [INF] -------------------------------------------
[21760] 2026/03/08 10:09:01.752241 [INF] Starting JetStream cluster
[21760] 2026/03/08 10:09:01.752241 [INF] Creating JetStream metadata controller
[21760] 2026/03/08 10:09:01.755384 [INF] JetStream cluster bootstrapping
[21760] 2026/03/08 10:09:01.760324 [INF] Took 10.6242ms to start JetStream
[21760] 2026/03/08 10:09:01.760324 [FTL] Can't start JetStream: sync C:\tmp\jetstream\4222\jetstream\$SYS\_js_\_meta_: Access is denied.
```


