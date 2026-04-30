# 🚀 Quick Start Guide - Kafka EDA Happy Flow

## 📋 Prerequisites
- Java 17+
- Maven
- Docker & Docker Compose

## ⚡ 3-Step Setup

### Step 1: Start Kafka (30 seconds)
```bash
cd EventDrivenArchitecture
docker-compose up -d
```

Wait for Kafka to be ready:
```bash
docker logs kafka -f
# Wait until you see: "Kafka Server started"
# Press Ctrl+C to exit logs
```

### Step 2: Start All Services (3 terminals)

**Terminal 1 - Producer (Port 8081):**
```bash
cd kafka-producer-service
mvn spring-boot:run
```

**Terminal 2 - Consumer (Port 8082):**
```bash
cd kafka-consumer-service
mvn spring-boot:run
```

**Terminal 3 - Analytics (Port 8083):**
```bash
cd kafka-analytics-consumer
mvn spring-boot:run
```

### Step 3: Test Happy Flow

**Option A - Use Test Script:**
```bash
./test-happy-flow.sh
```

**Option B - Manual Testing:**
```bash
# Create single order
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-123",
    "quantity": 2,
    "totalAmount": 299.99
  }'

# Create bulk orders
curl -X POST "http://localhost:8081/api/orders/bulk?count=10"
```

## 🎯 What to Observe

### In Producer Logs (Terminal 1):
```
📤 Sending order event - Key: ORDER-A1B2C3D4
✅ Order event sent successfully!
   Partition: 2
   Offset: 15
```

### In Consumer Logs (Terminal 2):
```
📨 Received order event from Kafka
   Partition: 2, Offset: 15
🔔 Sending notification for order: ORDER-A1B2C3D4
✅ Order event processed and offset committed
```

### In Analytics Logs (Terminal 3):
```
📊 Analytics: Received order event
   Partition: 2, Offset: 15
📈 Total Orders Processed: 42
```

## 🔍 Key Observations

1. **Same Event, Two Consumers**: Both consumer and analytics receive the same event
2. **Different Offsets**: Each consumer group maintains its own offset
3. **Partition Distribution**: Events distributed across 3 partitions based on orderId hash
4. **Parallel Processing**: 3 consumer threads per service (one per partition)

## 🎨 Kafka UI (Optional)

Open browser: http://localhost:8080

You can see:
- Topics and partitions
- Consumer groups and their offsets
- Messages in topics
- Partition leaders

## 🛑 Cleanup

```bash
# Stop services (Ctrl+C in each terminal)

# Stop Kafka
docker-compose down

# Remove volumes (optional - clears all data)
docker-compose down -v
```

## 📚 Next Steps

1. ✅ **Happy Flow** - You're here!
2. 🔄 **Rebalancing** - Stop one consumer, see partition reassignment
3. ⚠️ **Failure Scenarios** - Simulate broker/consumer failures
4. 🔄 **Offset Reset** - Reset consumer group to replay events
5. 📈 **Scaling** - Run multiple instances of same service

See `README.md` for detailed documentation.

