# Kafka Architecture - Engineering Deep Dive for Event-Driven Microservices

## Table of Contents
1. [Core Architecture Components](#1-core-architecture-components)
2. [Storage & Log Structure](#2-storage--log-structure)
3. [Replication & Fault Tolerance](#3-replication--fault-tolerance)
4. [Producer Architecture & Guarantees](#4-producer-architecture--guarantees)
5. [Consumer Architecture & Semantics](#5-consumer-architecture--semantics)
6. [Failure Scenarios & Recovery](#6-failure-scenarios--recovery)
7. [Performance & Scalability](#7-performance--scalability)
8. [Distributed System Challenges](#8-distributed-system-challenges)
9. [Production Configuration](#9-production-configuration)
10. [Monitoring & Observability](#10-monitoring--observability)

---

## 1. Core Architecture Components

### 1.1 Broker
**What it is:** A Kafka server that stores data and serves clients.

**Key Responsibilities:**
- **Log Management**: Manages partition replicas (leader or follower)
- **Request Handling**: Processes produce, fetch, metadata requests
- **Replication**: Synchronizes data across replicas
- **Coordination**: Participates in leader election via Controller

**Critical Internals:**
```
Broker Process
├── Network Thread Pool (num.network.threads=3)
│   └── Accepts connections, reads requests, writes responses
├── I/O Thread Pool (num.io.threads=8)
│   └── Processes requests, interacts with log
├── Log Manager
│   └── Manages all partition replicas on this broker
├── Replica Manager
│   └── Handles replication protocol (leader/follower logic)
└── Group Coordinator
    └── Manages consumer group membership & offsets
```

**Why it matters:**
- Single broker failure should NOT cause data loss (if RF ≥ 2)
- Brokers are stateful - disk failure = data loss for that replica
- Network threads are separate from I/O to prevent blocking

---

### 1.2 Topic & Partitions
**What it is:** Logical channel for messages, physically split into partitions.

**Partition = Ordered, Immutable Log**
```
Partition 0: [msg0][msg1][msg2][msg3]... → append-only
             offset=0  1    2    3
```

**Key Properties:**
- **Ordering**: Guaranteed ONLY within a partition
- **Parallelism**: # of partitions = max consumer parallelism
- **Immutability**: Messages never modified, only appended
- **Retention**: Time-based (7 days) or size-based (100GB)

**Partitioning Strategy:**
```java
// Producer determines partition
partition = hash(key) % num_partitions  // if key present
partition = round_robin                  // if key null
```

**Critical Design Decision:**
- **Too few partitions**: Limited parallelism, hot spots
- **Too many partitions**:
  - More file handles (each partition = 2 files minimum)
  - Longer leader election time
  - Higher memory overhead

**Rule of Thumb:** Start with `max(expected_throughput_MB/s / 10, num_consumers)`

---

### 1.3 ZooKeeper vs KRaft (Metadata Management)

#### ZooKeeper Mode (Legacy, < Kafka 3.x)
**Responsibilities:**
- Broker membership & liveness detection
- Controller election (exactly one controller per cluster)
- Topic/partition metadata
- ACLs, quotas, configurations

**Problems:**
- External dependency (operational complexity)
- Scalability limits (~100K partitions)
- Metadata propagation delays

#### KRaft Mode (Kafka 3.3+, Production-Ready)
**Architecture:**
- Raft-based consensus among Kafka brokers themselves
- Dedicated controller quorum (3 or 5 nodes)
- Metadata stored as Kafka topic `__cluster_metadata`

**Advantages:**
- Faster metadata propagation (ms vs seconds)
- Supports millions of partitions
- Simpler operations (one less system)

**Migration Path:** ZooKeeper → Dual-mode → KRaft-only

---

### 1.4 Controller
**What it is:** One broker elected as cluster controller.

**Responsibilities:**
1. **Leader Election**: Selects partition leaders when brokers fail
2. **Metadata Propagation**: Pushes metadata updates to all brokers
3. **Partition Reassignment**: Handles admin operations
4. **ISR Management**: Tracks In-Sync Replica set

**Election Process (ZooKeeper):**
```
1. All brokers try to create /controller ephemeral node in ZK
2. First one wins, becomes controller
3. If controller dies, ZK session expires, node deleted
4. Remaining brokers race to create node again
```

**Election Process (KRaft):**
```
1. Raft election among controller quorum
2. Leader elected via majority vote
3. Metadata changes replicated via Raft log
```

**Why Single Controller:**
- Avoids split-brain scenarios
- Simplifies metadata consistency
- Bottleneck for large clusters (mitigated in KRaft)

---

## 2. Storage & Log Structure

### 2.1 Physical Storage Layout
```
/var/lib/kafka/data/
└── topic-name-0/              # Partition directory
    ├── 00000000000000000000.log      # Segment file (1GB default)
    ├── 00000000000000000000.index    # Offset index
    ├── 00000000000000000000.timeindex # Timestamp index
    ├── 00000000000001000000.log      # Next segment
    ├── 00000000000001000000.index
    └── leader-epoch-checkpoint       # Fencing metadata
```

**Segment Files:**
- Active segment: Currently being written
- Closed segments: Immutable, eligible for deletion/compaction
- Segment rolls when: Size limit OR time limit reached

### 2.2 Message Format (v2 - Current)
```
Record Batch (compressed)
├── Base Offset: 1000
├── Length: 1024 bytes
├── CRC: checksum
├── Attributes: compression type, timestamp type
├── Producer ID: 12345 (for idempotence)
├── Producer Epoch: 0
├── Base Sequence: 0
└── Records: [
    ├── Record 0: {offset_delta=0, timestamp_delta=0, key, value, headers}
    ├── Record 1: {offset_delta=1, timestamp_delta=100, key, value, headers}
    └── ...
]
```

**Compression:**
- Applied at batch level (not individual messages)
- Algorithms: gzip, snappy, lz4, zstd
- Trade-off: CPU vs Network/Disk

### 2.3 Zero-Copy & Page Cache
**sendfile() System Call:**
```
Traditional:
Disk → Kernel Buffer → User Space → Socket Buffer → NIC
     (4 context switches, 4 data copies)

Zero-Copy:
Disk → Kernel Buffer → NIC
     (2 context switches, 0 data copies to user space)
```

**Page Cache Strategy:**
- Kafka relies on OS page cache (doesn't maintain own cache)
- Writes go to page cache, flushed to disk asynchronously
- Reads served from page cache if recently written
- OS manages cache eviction (LRU)

**Why it works:**
- Sequential I/O is fast even on HDD
- OS is better at caching than application-level caches
- Avoids double-buffering (JVM heap + OS cache)

---

## 3. Replication & Fault Tolerance

### 3.1 Replication Protocol

**Replication Factor (RF):** Number of copies of each partition.
```
Topic: orders, Partitions: 3, RF: 3
Partition 0: Broker 1 (Leader), Broker 2 (Follower), Broker 3 (Follower)
Partition 1: Broker 2 (Leader), Broker 1 (Follower), Broker 3 (Follower)
Partition 2: Broker 3 (Leader), Broker 1 (Follower), Broker 2 (Follower)
```

**Leader-Follower Model:**
- **Leader**: Handles all reads and writes for a partition
- **Follower**: Passively replicates from leader (acts like consumer)
- **ISR (In-Sync Replica)**: Followers that are "caught up" with leader

### 3.2 In-Sync Replicas (ISR)

**Definition:** Replicas that have:
1. Heartbeat to ZooKeeper/Controller within `replica.lag.time.max.ms` (10s default)
2. Fetched messages within `replica.lag.time.max.ms`

**ISR Dynamics:**
```
Initial State: ISR = [B1, B2, B3]

B3 falls behind (network issue):
→ Leader removes B3 from ISR
→ ISR = [B1, B2]

B3 catches up:
→ Leader adds B3 back to ISR
→ ISR = [B1, B2, B3]
```

**Critical Property:**
- Only ISR members are eligible for leader election
- Prevents data loss (leader always has latest data)

**Edge Case - All ISR Members Down:**
```
Config: unclean.leader.election.enable=false (default)
→ Partition becomes unavailable (no leader)
→ Waits for ISR member to recover
→ NO DATA LOSS

Config: unclean.leader.election.enable=true
→ Allows non-ISR replica to become leader
→ POTENTIAL DATA LOSS (messages not replicated to this broker)
→ Availability over consistency
```

### 3.3 High Watermark (HW) & Log End Offset (LEO)

**Definitions:**
- **LEO (Log End Offset)**: Last offset written to log (per replica)
- **HW (High Watermark)**: Offset up to which all ISR replicas have replicated

```
Leader:  [msg0][msg1][msg2][msg3][msg4]
         LEO=5, HW=3

Follower1: [msg0][msg1][msg2][msg3]
           LEO=4

Follower2: [msg0][msg1][msg2]
           LEO=3

HW = min(LEO of all ISR) = 3
Consumers can only read up to HW (offset 0-2)
```

**Why HW Matters:**
- Ensures consumers only see committed data (replicated to all ISR)
- Prevents reading uncommitted data that might be lost on leader failure

### 3.4 Leader Epoch (Fencing)

**Problem:** HW-based replication can cause data loss/divergence.

**Scenario:**
```
1. Leader B1: [msg0][msg1][msg2], HW=2, LEO=3
   Follower B2: [msg0][msg1], HW=2, LEO=2

2. B2 fetches, gets msg2, but B1 crashes before updating HW

3. B2 becomes leader (ISR=[B2]), HW=2, LEO=2
   B2 truncates to HW, loses msg2

4. B1 recovers, has msg2, but B2 is leader
   → Data divergence
```

**Solution: Leader Epoch**
```
Each leader gets monotonically increasing epoch number.
Messages tagged with (epoch, offset).

Recovery:
1. B1 recovers, asks B2 (current leader): "What's your epoch?"
2. B2 responds: "Epoch 1, started at offset 2"
3. B1 truncates to offset 2 (discards msg2 from epoch 0)
4. B1 fetches from B2, gets consistent state
```

**File:** `leader-epoch-checkpoint`
```
0 0    # Epoch 0 started at offset 0
1 2    # Epoch 1 started at offset 2
```

---

## 4. Producer Architecture & Guarantees

### 4.1 Producer Internals

**Producer Flow:**
```
Application Thread
    ↓
send(record) → Serializer → Partitioner → RecordAccumulator (batches)
                                                ↓
                                          Sender Thread
                                                ↓
                                          Network (to Broker)
```

**RecordAccumulator:**
- Batches messages per partition
- Configurable batch size (`batch.size=16KB`) and linger time (`linger.ms=0`)
- Trade-off: Latency vs Throughput

**Sender Thread:**
- Separate thread that sends batches to brokers
- Handles retries, timeouts, metadata updates
- Maintains in-flight requests per connection (`max.in.flight.requests.per.connection=5`)

### 4.2 Delivery Guarantees

**acks Configuration:**

**acks=0 (Fire and Forget):**
- Producer doesn't wait for broker acknowledgment
- **Guarantee:** At-most-once (messages can be lost)
- **Use Case:** Metrics, logs where loss is acceptable
- **Latency:** Lowest

**acks=1 (Leader Acknowledgment):**
- Producer waits for leader to write to local log
- **Guarantee:** At-least-once (duplicates possible on retry)
- **Risk:** Data loss if leader fails before replication
- **Use Case:** Balanced latency/durability

**acks=all / acks=-1 (ISR Acknowledgment):**
- Producer waits for all ISR replicas to acknowledge
- **Guarantee:** At-least-once (with retries) or Exactly-once (with idempotence)
- **Requirement:** `min.insync.replicas=2` (at least 2 ISR members)
- **Use Case:** Financial transactions, critical data


**Configuration Matrix:**
```
RF=3, min.insync.replicas=2, acks=all
→ Can tolerate 1 broker failure without data loss
→ Requires 2 out of 3 replicas to acknowledge

RF=3, min.insync.replicas=1, acks=all
→ Degrades to acks=1 behavior (only leader needed)
```

### 4.3 Idempotent Producer (Exactly-Once - Part 1)

**Problem:** Network retries cause duplicates.
```
Producer sends msg → Leader writes → ACK lost in network
Producer retries → Leader writes AGAIN → Duplicate
```

**Solution: Producer ID + Sequence Number**
```java
// Enable idempotence
props.put("enable.idempotence", true);
// Automatically sets:
// acks=all, retries=MAX_INT, max.in.flight.requests=5
```

**Mechanism:**
```
Producer gets unique PID (Producer ID) from broker.
Each message tagged with (PID, Epoch, Sequence Number).

Broker maintains:
Map<(PID, Partition), LastSequence>

On receive:
if (sequence == lastSequence + 1):
    write message, update lastSequence
elif (sequence <= lastSequence):
    discard (duplicate), send ACK
else:
    OutOfOrderSequenceException
```

**Guarantees:**
- Exactly-once per partition per producer session
- Survives producer retries
- Does NOT survive producer restarts (new PID assigned)

### 4.4 Transactional Producer (Exactly-Once - Part 2)

**Use Case:** Consume-Process-Produce pattern (stream processing).

**Problem:**
```
1. Consumer reads offset 100 from topic A
2. Process data
3. Produce to topic B
4. Commit offset 101 to topic A

If crash between step 3 and 4:
→ Reprocess offset 100
→ Duplicate write to topic B
```

**Solution: Transactions**
```java
props.put("transactional.id", "order-processor-1");
producer.initTransactions();

try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("topic-B", key, value));
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Mechanism:**
1. **Transaction Coordinator**: Broker managing transaction state
2. **Transaction Log**: Internal topic `__transaction_state`
3. **Two-Phase Commit**:
   - Phase 1: Write data + transaction markers to partitions
   - Phase 2: Write COMMIT/ABORT marker to transaction log

**Consumer Side:**
```java
props.put("isolation.level", "read_committed");
// Only reads committed transactions
// Filters out aborted transactions
```

**Guarantees:**
- Exactly-once across multiple partitions
- Atomic writes (all or nothing)
- Survives producer restarts (via `transactional.id`)

**Performance Impact:**
- ~3x latency increase (due to 2PC)
- Use only when exactly-once is required

---

## 5. Consumer Architecture & Semantics

### 5.1 Consumer Group Protocol

**Consumer Group:** Logical group of consumers sharing workload.

**Key Properties:**
- Each partition assigned to exactly ONE consumer in group
- Each consumer can handle multiple partitions
- Rebalancing when consumers join/leave

**Partition Assignment Strategies:**

**1. RangeAssignor (Default):**
```
Topic: orders, Partitions: [0,1,2]
Consumers: [C1, C2]

Assignment:
C1: [0, 1]  (2 partitions)
C2: [2]     (1 partition)

Problem: Uneven distribution with multiple topics
```

**2. RoundRobinAssignor:**
```
Partitions: [P0, P1, P2, P3, P4, P5]
Consumers: [C1, C2, C3]

Assignment:
C1: [P0, P3]
C2: [P1, P4]
C3: [P2, P5]

Better distribution, but doesn't consider consumer capacity
```

**3. StickyAssignor:**
```
Minimizes partition movement during rebalance.
Preserves existing assignments when possible.

Before: C1:[P0,P1], C2:[P2,P3]
C3 joins
After:  C1:[P0], C2:[P2], C3:[P1,P3]
        (Only P1 moved)
```

**4. CooperativeStickyAssignor (Recommended):**
```
Incremental rebalancing - doesn't stop all consumers.
Only affected partitions are revoked/reassigned.
```

### 5.2 Consumer Rebalancing

**Rebalance Triggers:**
1. Consumer joins group
2. Consumer leaves (graceful shutdown or heartbeat timeout)
3. Partition count changes
4. Consumer subscription changes

**Rebalance Protocol (Eager):**
```
1. STOP_THE_WORLD: All consumers stop consuming
2. JoinGroup: All consumers send subscriptions to coordinator
3. Coordinator selects leader consumer
4. Leader computes assignment (using assignor strategy)
5. SyncGroup: Leader sends assignment to coordinator
6. Coordinator distributes assignments to all consumers
7. Consumers resume from committed offsets
```

**Problem with Eager Rebalancing:**
- All consumers stop during rebalance (unavailability)
- Can take seconds for large groups

**Cooperative Rebalancing (Incremental):**
```
1. Consumers continue processing unaffected partitions
2. Only revoked partitions are stopped
3. Reassignment happens incrementally
4. Minimal downtime
```

**Configuration:**
```java
props.put("partition.assignment.strategy",
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
```


### 5.3 Offset Management

**Offset:** Position of consumer in partition log.

**Offset Storage:**
- **Old (<0.9):** ZooKeeper (doesn't scale)
- **Current:** Internal topic `__consumer_offsets` (50 partitions, RF=3)

**Commit Strategies:**

**1. Auto-Commit (Default):**
```java
props.put("enable.auto.commit", true);
props.put("auto.commit.interval.ms", 5000);

// Commits every 5 seconds in background
```
**Risk:** At-least-once (duplicates on rebalance)
```
Poll batch: [msg0, msg1, msg2]
Process msg0, msg1
Auto-commit triggers (commits offset 3)
Crash before processing msg2
→ msg2 lost (but offset committed)
```

**2. Manual Commit (Synchronous):**
```java
props.put("enable.auto.commit", false);

while (true) {
    ConsumerRecords<> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<> record : records) {
        process(record);
    }
    consumer.commitSync(); // Blocks until committed
}
```
**Guarantee:** At-least-once (duplicates on crash before commit)

**3. Manual Commit (Asynchronous):**
```java
consumer.commitAsync((offsets, exception) -> {
    if (exception != null) {
        log.error("Commit failed", exception);
    }
});
```
**Benefit:** Non-blocking, higher throughput
**Risk:** No retry on failure (could lose offset)

**4. Per-Record Commit (Slow but Safe):**
```java
for (ConsumerRecord<> record : records) {
    process(record);
    consumer.commitSync(Collections.singletonMap(
        new TopicPartition(record.topic(), record.partition()),
        new OffsetAndMetadata(record.offset() + 1)
    ));
}
```

**5. Transactional Consume-Process-Produce:**
```java
// Producer side (see section 4.4)
producer.sendOffsetsToTransaction(offsets, groupId);
producer.commitTransaction();

// Consumer side
props.put("isolation.level", "read_committed");
```
**Guarantee:** Exactly-once end-to-end

### 5.4 Consumer Delivery Semantics

**At-Most-Once (Commit before processing):**
```java
ConsumerRecords<> records = consumer.poll(Duration.ofMillis(100));
consumer.commitSync(); // Commit first
for (ConsumerRecord<> record : records) {
    process(record); // If crash here, message lost
}
```

**At-Least-Once (Commit after processing):**
```java
ConsumerRecords<> records = consumer.poll(Duration.ofMillis(100));
for (ConsumerRecord<> record : records) {
    process(record);
}
consumer.commitSync(); // If crash before commit, reprocess
```

**Exactly-Once (Idempotent processing or transactions):**
```java
// Option 1: Idempotent processing
for (ConsumerRecord<> record : records) {
    processIdempotently(record); // e.g., upsert with unique key
}
consumer.commitSync();

// Option 2: Transactional (see 5.3.5)
```

### 5.5 Consumer Lag & Monitoring

**Consumer Lag:** Difference between latest offset and consumer's committed offset.

```
Partition: [msg0][msg1][msg2][msg3][msg4][msg5]
           offset=0  1    2    3    4    5

Latest Offset (LEO): 6
Consumer Committed Offset: 3
Lag: 6 - 3 = 3 messages
```

**Monitoring:**
```bash
# Check lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group my-group

GROUP     TOPIC     PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
my-group  orders    0          1000            1500            500
my-group  orders    1          2000            2000            0
```

**Lag Causes:**
1. Consumer slower than producer (throughput mismatch)
2. Consumer doing expensive processing
3. Consumer rebalancing frequently
4. Network issues

**Solutions:**
- Scale consumers (add more instances)
- Optimize processing logic
- Increase `max.poll.records` (process more per poll)
- Increase `fetch.min.bytes` (batch more data)

---

## 6. Failure Scenarios & Recovery

### 6.1 Broker Failure

**Scenario 1: Follower Broker Fails**
```
Before: Leader=B1, ISR=[B1,B2,B3]
B3 fails
After:  Leader=B1, ISR=[B1,B2]

Impact: None (leader still available)
Recovery: B3 restarts, catches up, rejoins ISR
```

**Scenario 2: Leader Broker Fails**
```
Before: Leader=B1, ISR=[B1,B2,B3]
B1 fails
Controller detects failure (ZK session timeout or heartbeat)
Controller selects new leader from ISR (B2 or B3)
After:  Leader=B2, ISR=[B2,B3]

Impact: Brief unavailability (election time ~few ms to seconds)
Data Loss: None (if min.insync.replicas=2, acks=all)
```

**Scenario 3: All ISR Members Fail**
```
Before: Leader=B1, ISR=[B1,B2], B3 out of sync
B1 and B2 fail simultaneously

Option A: unclean.leader.election.enable=false
→ Partition unavailable until B1 or B2 recovers
→ No data loss

Option B: unclean.leader.election.enable=true
→ B3 becomes leader (not in ISR)
→ Data loss (messages not replicated to B3)
```

### 6.2 Network Partition

**Scenario: Split Brain Prevention**
```
Cluster: 3 brokers, 5 ZK nodes
Network partition: [B1, B2, ZK1, ZK2] | [B3, ZK3, ZK4, ZK5]

Side 1 (Minority):
- Lost ZK quorum (2/5 nodes)
- B1, B2 cannot elect new leaders
- Existing leaders step down
- Partitions unavailable

Side 2 (Majority):
- Has ZK quorum (3/5 nodes)
- B3 can become leader for partitions
- Continues operation

Recovery:
- Network heals
- B1, B2 rejoin cluster
- Sync from current leaders
```

**Why ZK Quorum Matters:**
- Prevents split-brain (two leaders for same partition)
- Ensures single source of truth
- Requires majority (N/2 + 1) for decisions



### 6.3 Producer Failure

**Scenario 1: Producer Crashes Mid-Send**
```
Without Idempotence:
Producer sends batch → Broker writes → ACK lost → Producer crashes
New producer instance → Resends batch → Duplicates

With Idempotence:
Producer sends (PID=123, Seq=0) → Broker writes → ACK lost → Producer crashes
New producer instance → Gets new PID=456 → Sends (PID=456, Seq=0)
→ Different PID, treated as new message (duplicate)

With Transactions:
Producer sends (TxnID=order-producer-1) → Crash before commit
New producer instance → Same TxnID → Aborts previous transaction
→ No duplicates
```

**Configuration for Resilience:**
```java
props.put("enable.idempotence", true);
props.put("transactional.id", "unique-producer-id"); // For exactly-once
props.put("acks", "all");
props.put("retries", Integer.MAX_VALUE);
props.put("max.in.flight.requests.per.connection", 5);
```

### 6.4 Consumer Failure

**Scenario 1: Consumer Crashes Before Commit**
```
Consumer polls: [msg0, msg1, msg2]
Processes: msg0, msg1, msg2
Crashes before commitSync()

Recovery:
New consumer instance joins group
Rebalance triggered
Reads from last committed offset
Reprocesses msg0, msg1, msg2 (duplicates)
```

**Mitigation:**
- Idempotent processing (database upserts, deduplication)
- Transactional processing
- Frequent commits (trade-off with performance)

**Scenario 2: Consumer Heartbeat Timeout**
```
Consumer processing long-running task (> max.poll.interval.ms)
Coordinator marks consumer as dead
Triggers rebalance
Consumer finishes processing, tries to commit
→ CommitFailedException (no longer owns partition)
```

**Configuration:**
```java
props.put("max.poll.interval.ms", 300000); // 5 minutes
props.put("max.poll.records", 500); // Process fewer records per poll
props.put("session.timeout.ms", 10000); // Heartbeat timeout
props.put("heartbeat.interval.ms", 3000); // Heartbeat frequency
```

### 6.5 Disk Failure

**Scenario: Broker Disk Corruption**
```
Broker B1 disk fails
→ All partition replicas on B1 lost
→ If B1 was leader: Controller elects new leader from ISR
→ If B1 was follower: Removed from ISR

Recovery:
1. Replace disk
2. Restart broker
3. Broker rejoins cluster
4. Replicas re-sync from leaders (full copy)
```

**RAID Configuration:**
- RAID 10 (striping + mirroring) for durability
- JBOD (Just a Bunch of Disks) for capacity (Kafka handles replication)

**Data Loss Prevention:**
- RF ≥ 3
- min.insync.replicas ≥ 2
- acks=all
- Regular backups (MirrorMaker, Kafka Connect)

---

## 7. Performance & Scalability

### 7.1 Throughput Optimization

**Producer Side:**
```java
// Batching
props.put("batch.size", 32768); // 32KB (default 16KB)
props.put("linger.ms", 10); // Wait 10ms to batch more messages

// Compression
props.put("compression.type", "lz4"); // Fast compression

// Buffer
props.put("buffer.memory", 67108864); // 64MB (default 32MB)

// Parallelism
props.put("max.in.flight.requests.per.connection", 5);
```

**Expected Throughput:**
- Single producer: 50-100 MB/s
- With compression: 100-200 MB/s
- Multiple producers: Linear scaling

**Consumer Side:**
```java
// Fetch more data per request
props.put("fetch.min.bytes", 1024); // 1KB
props.put("fetch.max.wait.ms", 500); // Wait up to 500ms

// Process more records per poll
props.put("max.poll.records", 1000); // Default 500

// Larger receive buffer
props.put("receive.buffer.bytes", 65536); // 64KB
```

**Expected Throughput:**
- Single consumer: 100-200 MB/s (zero-copy helps)
- Multiple consumers: Linear scaling (up to # partitions)

### 7.2 Latency Optimization

**Producer Side:**
```java
// Minimize batching
props.put("linger.ms", 0); // Send immediately
props.put("batch.size", 1); // No batching

// Reduce retries
props.put("retries", 0); // No retries (risky)

// Faster acks
props.put("acks", "1"); // Leader only (not all ISR)
```

**Expected Latency:**
- p50: 2-5 ms
- p99: 10-20 ms
- p99.9: 50-100 ms

**Broker Side:**
```
# Reduce log flush delay
log.flush.interval.messages=1
log.flush.interval.ms=1

# Warning: Impacts throughput significantly
```

**Consumer Side:**
```java
// Poll frequently
props.put("fetch.min.bytes", 1); // Don't wait for batching
props.put("fetch.max.wait.ms", 0); // Return immediately
```

### 7.3 Scalability Limits

**Partition Limits:**
```
Per Broker: ~4,000 partitions (file handle limits)
Per Cluster: ~200,000 partitions (ZooKeeper mode)
Per Cluster: Millions (KRaft mode)

Calculation:
Each partition = 2 file handles (log + index)
OS limit: ulimit -n (default 1024, increase to 100,000)
```

**Consumer Group Limits:**
```
Max consumers per group = # of partitions
More consumers than partitions → idle consumers

Example:
Topic with 10 partitions
Consumer group with 15 consumers
→ 10 active, 5 idle
```

**Broker Limits:**
```
Network: 1-10 Gbps NIC
Disk: 500 MB/s (SSD), 100 MB/s (HDD)
Memory: 64-128 GB (mostly for page cache)
CPU: 16-32 cores (compression, encryption)
```

### 7.4 Overload Scenarios

**Scenario 1: Producer Overload**
```
Producer sends faster than broker can write
→ RecordAccumulator fills up
→ BufferExhaustedException

Mitigation:
- Increase buffer.memory
- Add more brokers
- Add more partitions
- Enable compression
```

**Scenario 2: Consumer Lag**
```
Consumer slower than producer
→ Lag increases continuously
→ Eventually runs out of retention

Mitigation:
- Scale consumers (add instances)
- Optimize processing logic
- Increase retention period
- Use separate consumer groups for different SLAs
```

**Scenario 3: Broker Overload**
```
Too many requests per broker
→ High CPU, disk I/O
→ Increased latency, timeouts

Mitigation:
- Add more brokers
- Rebalance partition leaders (kafka-reassign-partitions.sh)
- Increase num.io.threads, num.network.threads
- Use faster disks (SSD)
```

**Scenario 4: Rebalance Storm**
```
Many consumers rebalancing simultaneously
→ Stop-the-world pauses
→ Lag spikes

Mitigation:
- Use CooperativeStickyAssignor
- Increase session.timeout.ms
- Increase max.poll.interval.ms
- Stagger consumer deployments
```

---

## 8. Distributed System Challenges

### 8.1 CAP Theorem Trade-offs

**Kafka's Choice: CP (Consistency + Partition Tolerance)**

```
Consistency: All ISR replicas must acknowledge (acks=all)
Availability: Partition unavailable if no ISR leader
Partition Tolerance: Handles network partitions via quorum

Trade-off:
- Prefer consistency over availability
- unclean.leader.election.enable=false (default)
- Partition unavailable rather than serve stale data
```

**Tuning for Availability (AP):**
```
unclean.leader.election.enable=true
min.insync.replicas=1
acks=1

Risk: Data loss, but higher availability
```

### 8.2 Clock Skew & Timestamps

**Problem:** Distributed systems have clock drift.

**Kafka's Approach:**
```
Message timestamps:
- CreateTime: Producer's timestamp (default)
- LogAppendTime: Broker's timestamp

Broker uses its own clock for:
- Log retention (time-based)
- Timestamp index
- Transaction timeouts
```

**Best Practice:**
```
- Use NTP/Chrony for clock synchronization
- Monitor clock skew (< 100ms acceptable)
- Use LogAppendTime for critical ordering
```

### 8.3 Exactly-Once Semantics Limitations

**What Exactly-Once Guarantees:**
```
✓ No duplicates within Kafka (producer to broker)
✓ Atomic multi-partition writes (transactions)
✓ Consume-process-produce exactly-once (Kafka Streams)
```

**What It Does NOT Guarantee:**
```
✗ External system writes (database, API calls)
✗ Side effects (sending emails, notifications)
✗ Non-idempotent operations
```

**Solution for External Systems:**
```java
// Option 1: Idempotent operations
database.upsert(key, value); // Safe to retry

// Option 2: Deduplication
if (!database.exists(messageId)) {
    database.insert(messageId, value);
}

// Option 3: Transactional outbox pattern
database.beginTransaction();
database.insert(data);
database.insert(outbox_event);
database.commit();
// Separate process reads outbox and publishes to Kafka
```

### 8.4 Ordering Guarantees

**Within Partition: Total Order**
```
Producer sends: [msg1, msg2, msg3] to Partition 0
Consumer reads: [msg1, msg2, msg3] (same order)
```

**Across Partitions: No Guarantee**
```
Producer sends:
  Partition 0: [msg1, msg3]
  Partition 1: [msg2, msg4]

Consumer may read: [msg1, msg2, msg3, msg4] OR [msg2, msg1, msg4, msg3]
```

**Maintaining Order:**
```java
// Use same key for related messages
producer.send(new ProducerRecord<>("topic", userId, event));
// All events for same userId go to same partition
```

**Caveat: Retries Can Reorder**
```
max.in.flight.requests.per.connection > 1
enable.idempotence=false

Batch 1 (msg1) sent → Fails
Batch 2 (msg2) sent → Succeeds
Batch 1 retried → Succeeds
Result: [msg2, msg1] (reordered!)

Solution: enable.idempotence=true (prevents reordering)
```

---

## 9. Production Configuration

### 9.1 Broker Configuration (Production-Ready)

```properties
############################# Server Basics #############################
broker.id=1
listeners=PLAINTEXT://0.0.0.0:9092
advertised.listeners=PLAINTEXT://kafka-broker-1.example.com:9092
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

############################# Log Basics #############################
log.dirs=/var/lib/kafka/data
num.partitions=3
default.replication.factor=3
min.insync.replicas=2

############################# Log Retention #############################
log.retention.hours=168  # 7 days
log.retention.bytes=1073741824  # 1GB per partition
log.segment.bytes=1073741824  # 1GB segments
log.retention.check.interval.ms=300000  # 5 minutes

############################# Replication #############################
replica.lag.time.max.ms=10000
replica.fetch.max.bytes=1048576
num.replica.fetchers=4
replica.socket.timeout.ms=30000
replica.socket.receive.buffer.bytes=65536

############################# Leader Election #############################
unclean.leader.election.enable=false
auto.leader.rebalance.enable=true
leader.imbalance.per.broker.percentage=10
leader.imbalance.check.interval.seconds=300

############################# Group Coordinator #############################
group.initial.rebalance.delay.ms=3000
offsets.topic.replication.factor=3
offsets.topic.num.partitions=50
transaction.state.log.replication.factor=3
transaction.state.log.min.isr=2

############################# Compression #############################
compression.type=producer  # Use producer's compression

############################# ZooKeeper (if not using KRaft) #############################
zookeeper.connect=zk1:2181,zk2:2181,zk3:2181
zookeeper.connection.timeout.ms=18000

############################# Performance #############################
num.recovery.threads.per.data.dir=1
log.flush.interval.messages=10000
log.flush.interval.ms=1000
```

### 9.2 Producer Configuration (Production-Ready)

```java
Properties props = new Properties();

// Connection
props.put("bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092");
props.put("client.id", "order-service-producer");

// Serialization
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

// Reliability (Exactly-Once)
props.put("enable.idempotence", true);
props.put("acks", "all");
props.put("retries", Integer.MAX_VALUE);
props.put("max.in.flight.requests.per.connection", 5);

// Transactions (if needed)
props.put("transactional.id", "order-service-txn-1");

// Performance
props.put("compression.type", "lz4");
props.put("batch.size", 32768);  // 32KB
props.put("linger.ms", 10);
props.put("buffer.memory", 67108864);  // 64MB

// Timeouts
props.put("request.timeout.ms", 30000);
props.put("delivery.timeout.ms", 120000);

// Partitioning
props.put("partitioner.class", "org.apache.kafka.clients.producer.internals.DefaultPartitioner");
```

### 9.3 Consumer Configuration (Production-Ready)

```java
Properties props = new Properties();

// Connection
props.put("bootstrap.servers", "kafka1:9092,kafka2:9092,kafka3:9092");
props.put("group.id", "order-processing-group");
props.put("client.id", "order-consumer-1");

// Deserialization
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

// Offset Management
props.put("enable.auto.commit", false);  // Manual commit for reliability
props.put("auto.offset.reset", "earliest");  // Start from beginning if no offset

// Transactions (if producer uses transactions)
props.put("isolation.level", "read_committed");

// Performance
props.put("fetch.min.bytes", 1024);
props.put("fetch.max.wait.ms", 500);
props.put("max.poll.records", 500);
props.put("max.partition.fetch.bytes", 1048576);  // 1MB

// Heartbeat & Session
props.put("session.timeout.ms", 10000);
props.put("heartbeat.interval.ms", 3000);
props.put("max.poll.interval.ms", 300000);  // 5 minutes

// Partition Assignment
props.put("partition.assignment.strategy",
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");
```

### 9.4 Topic Configuration

```bash
# Create topic with proper configuration
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --partitions 12 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=604800000 \  # 7 days
  --config segment.ms=86400000 \     # 1 day
  --config compression.type=lz4 \
  --config cleanup.policy=delete \
  --config max.message.bytes=1048576  # 1MB
```

**Partition Count Calculation:**
```
Target throughput: 120 MB/s
Single partition throughput: 10 MB/s
Partitions needed: 120 / 10 = 12

Add buffer for growth: 12 * 1.5 = 18 partitions
```

### 9.5 OS & JVM Tuning

**OS Configuration:**
```bash
# File descriptors
ulimit -n 100000

# Disable swap
swapoff -a

# Disk I/O scheduler (for SSD)
echo noop > /sys/block/sda/queue/scheduler

# Network tuning
sysctl -w net.core.rmem_max=134217728
sysctl -w net.core.wmem_max=134217728
sysctl -w net.ipv4.tcp_rmem="4096 87380 134217728"
sysctl -w net.ipv4.tcp_wmem="4096 65536 134217728"
```

**JVM Configuration:**
```bash
# Kafka broker JVM options
export KAFKA_HEAP_OPTS="-Xms6g -Xmx6g"
export KAFKA_JVM_PERFORMANCE_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=20 \
  -XX:InitiatingHeapOccupancyPercent=35 -XX:G1HeapRegionSize=16M \
  -XX:MinMetaspaceSize=96m -XX:MaxMetaspaceSize=256m"

# GC logging
export KAFKA_GC_LOG_OPTS="-Xlog:gc*:file=/var/log/kafka/gc.log:time,tags:filecount=10,filesize=100M"
```

**Heap Size Guidelines:**
```
Small cluster (< 10 brokers): 4-6 GB
Medium cluster (10-50 brokers): 6-8 GB
Large cluster (> 50 brokers): 8-12 GB

Don't exceed 12 GB (GC overhead)
Leave 50% of RAM for page cache
```

---

## 10. Monitoring & Observability

### 10.1 Key Metrics to Monitor

**Broker Metrics:**
```
# Throughput
kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec
kafka.server:type=BrokerTopicMetrics,name=BytesInPerSec
kafka.server:type=BrokerTopicMetrics,name=BytesOutPerSec

# Request Latency
kafka.network:type=RequestMetrics,name=TotalTimeMs,request=Produce
kafka.network:type=RequestMetrics,name=TotalTimeMs,request=FetchConsumer

# Under-replicated Partitions (CRITICAL)
kafka.server:type=ReplicaManager,name=UnderReplicatedPartitions
# Should be 0 in healthy cluster

# Offline Partitions (CRITICAL)
kafka.controller:type=KafkaController,name=OfflinePartitionsCount
# Should be 0

# ISR Shrink/Expand Rate
kafka.server:type=ReplicaManager,name=IsrShrinksPerSec
kafka.server:type=ReplicaManager,name=IsrExpandsPerSec

# Leader Election Rate
kafka.controller:type=ControllerStats,name=LeaderElectionRateAndTimeMs
```

**Producer Metrics:**
```
# Record Send Rate
kafka.producer:type=producer-metrics,client-id=*,name=record-send-rate

# Record Error Rate
kafka.producer:type=producer-metrics,client-id=*,name=record-error-rate

# Request Latency
kafka.producer:type=producer-metrics,client-id=*,name=request-latency-avg

# Buffer Availability
kafka.producer:type=producer-metrics,client-id=*,name=buffer-available-bytes
```

**Consumer Metrics:**
```
# Consumer Lag (CRITICAL)
kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*,name=records-lag-max

# Fetch Rate
kafka.consumer:type=consumer-fetch-manager-metrics,client-id=*,name=fetch-rate

# Commit Latency
kafka.consumer:type=consumer-coordinator-metrics,client-id=*,name=commit-latency-avg

# Rebalance Rate
kafka.consumer:type=consumer-coordinator-metrics,client-id=*,name=rebalance-rate-per-hour
```

### 10.2 Alerting Rules

**Critical Alerts:**
```yaml
# Under-replicated partitions
- alert: KafkaUnderReplicatedPartitions
  expr: kafka_server_replicamanager_underreplicatedpartitions > 0
  for: 5m
  severity: critical

# Offline partitions
- alert: KafkaOfflinePartitions
  expr: kafka_controller_kafkacontroller_offlinepartitionscount > 0
  for: 1m
  severity: critical

# Consumer lag
- alert: KafkaConsumerLag
  expr: kafka_consumer_lag > 10000
  for: 10m
  severity: warning
```

**Warning Alerts:**
```yaml
# High ISR shrink rate
- alert: KafkaHighISRShrinkRate
  expr: rate(kafka_server_replicamanager_isrshrinkspers[5m]) > 0.1
  for: 5m
  severity: warning

# High request latency
- alert: KafkaHighProduceLatency
  expr: kafka_network_requestmetrics_totaltimems{request="Produce"} > 100
  for: 5m
  severity: warning
```

### 10.3 Distributed Tracing

**Spring Boot + Kafka Integration:**
```java
@Configuration
public class KafkaTracingConfig {

    @Bean
    public ProducerFactory<String, String> producerFactory(Tracer tracer) {
        Map<String, Object> props = new HashMap<>();
        // ... standard config

        DefaultKafkaProducerFactory<String, String> factory =
            new DefaultKafkaProducerFactory<>(props);

        // Add tracing interceptor
        factory.setProducerInterceptor(new TracingProducerInterceptor<>(tracer));
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(Tracer tracer) {
        Map<String, Object> props = new HashMap<>();
        // ... standard config

        DefaultKafkaConsumerFactory<String, String> factory =
            new DefaultKafkaConsumerFactory<>(props);

        // Add tracing interceptor
        factory.setConsumerInterceptor(new TracingConsumerInterceptor<>(tracer));
        return factory;
    }
}
```

### 10.4 Logging Best Practices

**Structured Logging:**
```java
@Slf4j
@Component
public class OrderEventProducer {

    public void sendOrder(Order order) {
        try {
            ProducerRecord<String, String> record =
                new ProducerRecord<>("orders", order.getId(), order.toJson());

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    log.error("Failed to send order event",
                        kv("orderId", order.getId()),
                        kv("topic", metadata.topic()),
                        kv("partition", metadata.partition()),
                        exception);
                } else {
                    log.info("Order event sent successfully",
                        kv("orderId", order.getId()),
                        kv("topic", metadata.topic()),
                        kv("partition", metadata.partition()),
                        kv("offset", metadata.offset()));
                }
            });
        } catch (Exception e) {
            log.error("Error producing order event",
                kv("orderId", order.getId()), e);
        }
    }
}
```

---

## Summary: Decision Matrix for Developers

### When to Use What Configuration

| Requirement | Configuration |
|-------------|---------------|
| **No data loss** | `acks=all`, `min.insync.replicas=2`, `RF=3` |
| **Exactly-once** | `enable.idempotence=true`, `transactional.id=...` |
| **Low latency** | `linger.ms=0`, `acks=1`, `compression.type=none` |
| **High throughput** | `linger.ms=10`, `batch.size=32KB`, `compression.type=lz4` |
| **Ordered processing** | Same key for related messages, `enable.idempotence=true` |
| **At-least-once** | Manual commit after processing |
| **At-most-once** | Auto-commit or commit before processing |

### Failure Tolerance Matrix

| Scenario | RF=3, min.isr=2 | RF=3, min.isr=1 | RF=2, min.isr=1 |
|----------|-----------------|-----------------|-----------------|
| 1 broker down | ✅ No data loss | ✅ No data loss | ✅ No data loss |
| 2 brokers down | ❌ Unavailable | ✅ Available (risk) | ❌ Unavailable |
| Network partition | ✅ Majority wins | ⚠️ May lose data | ⚠️ May lose data |

### Performance Benchmarks (Typical)

| Metric | Value |
|--------|-------|
| Producer throughput (single) | 50-100 MB/s |
| Consumer throughput (single) | 100-200 MB/s |
| End-to-end latency (p99) | 10-50 ms |
| Partitions per broker | ~4,000 |
| Max message size | 1 MB (default) |
| Retention (default) | 7 days |

---

**End of Document**

This architecture guide covers the essential engineering details needed to design and implement a production-grade event-driven microservices system using Kafka. Each section addresses real-world distributed system challenges with concrete solutions and configurations.

