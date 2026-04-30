#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Kafka EDA Happy Flow Test Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check if services are running
echo -e "${YELLOW}Checking if services are running...${NC}"
if ! curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Producer service (8081) not running. Please start it first.${NC}"
fi

if ! curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Consumer service (8082) not running. Please start it first.${NC}"
fi

if ! curl -s http://localhost:8083/actuator/health > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠️  Analytics service (8083) not running. Please start it first.${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test 1: Create Single Order${NC}"
echo -e "${GREEN}========================================${NC}"
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "productId": "PROD-123",
    "quantity": 2,
    "totalAmount": 299.99
  }' | jq .

echo ""
echo -e "${YELLOW}✓ Check logs in consumer services to see event processing${NC}"
sleep 2

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test 2: Create Order with Sync Confirmation${NC}"
echo -e "${GREEN}========================================${NC}"
curl -X POST http://localhost:8081/api/orders/sync \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-002",
    "productId": "PROD-456",
    "quantity": 1,
    "totalAmount": 149.99
  }' | jq .

echo ""
sleep 2

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test 3: Create Bulk Orders (10)${NC}"
echo -e "${GREEN}========================================${NC}"
curl -X POST "http://localhost:8081/api/orders/bulk?count=10" | jq .

echo ""
echo -e "${YELLOW}✓ Check logs to see partition distribution${NC}"
sleep 2

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Test 4: Create More Bulk Orders (20)${NC}"
echo -e "${GREEN}========================================${NC}"
curl -X POST "http://localhost:8081/api/orders/bulk?count=20" | jq .

echo ""
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Test Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Check producer logs (port 8081) - See partition assignment"
echo "2. Check consumer logs (port 8082) - See notification processing"
echo "3. Check analytics logs (port 8083) - See analytics updates"
echo "4. Open Kafka UI: http://localhost:8080"
echo ""
echo -e "${YELLOW}Useful Kafka Commands:${NC}"
echo "View topics:"
echo "  docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list"
echo ""
echo "View consumer groups:"
echo "  docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list"
echo ""
echo "View consumer group offsets:"
echo "  docker exec -it kafka kafka-consumer-groups --bootstrap-server localhost:9092 \\"
echo "    --describe --group notification-service-group"
echo ""

