# Event-Driven Architecture (EDA) - Kafka Demo

This package demonstrates **Kafka Event-Driven Architecture** with multiple microservices showcasing all the concepts from the Kafka Learning Guide.

## 📦 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    EDA ARCHITECTURE                             │
└─────────────────────────────────────────────────────────────────┘

Producer:
┌──────────────────────────┐
│ kafka-producer-service   │  Port: 8081
│ (OrderService)           │  
│                          │  Produces order events
│ POST /api/orders         │  Key: orderId
└──────────────────────────┘  Topic: order-events (3 partitions)
         │
         ↓
┌──────────────────────────┐
│   Apache Kafka Broker    │  Port: 9092
│                          │
│ Topics:                  │
│  - order-events (3 part) │
│  - payment-events        │
└──────────────────────────┘
         │
         ├─────────────────────────────────┐
         ↓                                 ↓
┌──────────────────────────┐    ┌──────────────────────────┐
│ kafka-consumer-service   │    │ kafka-analytics-consumer │
│ (NotificationService)    │    │ (AnalyticsService)       │
│                          │    │                          │
│ Port: 8082               │    │ Port: 8083               │
│ Group: notification-     │    │ Group: analytics-        │
│        service-group     │    │        service-group     │
│                          │    │                          │
│ Manual Commit            │    │ Auto Commit              │
│ At-least-once delivery   │    │ At-most-once delivery    │
└──────────────────────────┘    └──────────────────────────┘
```

## 🎯 What This Demo Shows

### ✅ Kafka Concepts Demonstrated:
1. **Producer** - Sending events to Kafka with key-based partitioning
2. **Topic & Partitions** - order-events topic with 3 partitions
3. **Consumer Groups** - Two different groups consuming same topic
4. **Offset Management** - Manual commit vs Auto commit
5. **Partition Assignment** - Hash-based routing using orderId as key
6. **Delivery Guarantees** - At-least-once vs At-most-once
7. **Concurrency** - Multiple consumers in same group (3 threads)

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven
- Docker & Docker Compose

### Step 1: Start Kafka

```bash
cd EventDrivenArchitecture
docker-compose up -d
```

This starts:
- Kafka broker on `localhost:9092`
- Kafka UI on `http://localhost:8080` (optional, for visualization)

### Step 2: Start All Services

**Terminal 1 - Producer Service:**
```bash
cd kafka-producer-service
mvn spring-boot:run
```

**Terminal 2 - Consumer Service:**
```bash
cd kafka-consumer-service
mvn spring-boot:run
```

**Terminal 3 - Analytics Consumer:**
```bash
cd kafka-analytics-consumer
mvn spring-boot:run
```

### Step 3: Test the Happy Flow

**Create a single order:**
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-123",
    "quantity": 2,
    "totalAmount": 299.99
  }'
```

**Create bulk orders (for testing partitioning):**
```bash
curl -X POST "http://localhost:8081/api/orders/bulk?count=10"
```

**Create order with sync confirmation:**
```bash
curl -X POST http://localhost:8081/api/orders/sync \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-002",
    "productId": "PROD-456",
    "quantity": 1,
    "totalAmount": 149.99
  }'
```

## 📊 Expected Output

### Producer Service (Port 8081)
```
📤 Sending order event - Key: ORDER-A1B2C3D4, Topic: order-events
✅ Order event sent successfully!
   Topic: order-events
   Partition: 2
   Offset: 15
   Timestamp: 1708387200000
```

### Consumer Service (Port 8082)
```
📨 Received order event from Kafka
   Topic: order-events
   Partition: 2
   Offset: 15
   Key: ORDER-A1B2C3D4
   Timestamp: 1708387200000
🔔 Sending notification for order: ORDER-A1B2C3D4
📧 Email sent to customer for order: ORDER-A1B2C3D4
📱 SMS sent to customer for order: ORDER-A1B2C3D4
✅ Order event processed and offset committed
```

### Analytics Consumer (Port 8083)
```
📊 Analytics: Received order event
   Partition: 2, Offset: 15, Key: ORDER-A1B2C3D4
📈 Analytics Update:
   Total Orders Processed: 42
   Total Revenue: $12,599
```

## 🔍 Key Observations

### 1. Same Event, Two Consumer Groups
- Both consumers receive the **same event**
- Each maintains **independent offsets**
- Demonstrates **pub-sub pattern**

### 2. Partition Assignment
- Events with same `orderId` → same partition
- Hash(orderId) % 3 = partition number
- Ensures **ordering per order**

### 3. Offset Commit Strategies
- **Notification Service**: Manual commit after processing (at-least-once)
- **Analytics Service**: Auto-commit (at-most-once, acceptable for analytics)

### 4. Concurrency
- Notification service runs 3 consumer threads
- Each thread reads from different partition
- Demonstrates **parallel processing**

## 🛠️ Useful Commands

**View Kafka Topics:**
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**View Topic Details:**
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 \
  --describe --topic order-events
```

**View Consumer Groups:**
```bash
docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list
```

**View Consumer Group Offsets:**
```bash
docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group notification-service-group
```

## 📚 Next Steps

After testing the happy flow, explore:
1. **Failure Scenarios** - Stop a consumer, see rebalancing
2. **Offset Reset** - Reset consumer group to earliest/latest
3. **Scaling** - Run multiple instances of same service
4. **Monitoring** - Use Kafka UI to visualize partitions and offsets

## 🎓 Learning Resources

See `KAFKA_LEARNING_GUIDE.md` in the root directory for comprehensive Kafka architecture documentation.

