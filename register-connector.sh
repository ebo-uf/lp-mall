#!/bin/bash

echo "Waiting for Kafka Connect to be ready..."
until curl -s http://localhost:8086/connectors > /dev/null; do
  sleep 2
done

echo "Deleting existing connector if present..."
curl -s -X DELETE http://localhost:8086/connectors/lp-mall-outbox-connector > /dev/null
sleep 2

echo "Registering Debezium connector..."
curl -X POST http://localhost:8086/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "lp-mall-outbox-connector",
    "config": {
      "connector.class": "io.debezium.connector.mysql.MySqlConnector",
      "database.hostname": "mysql",
      "database.port": "3306",
      "database.user": "debezium",
      "database.password": "debezium",
      "database.server.id": "1",
      "topic.prefix": "lp",
      "table.include.list": "order_db.outbox,product_db.outbox,payment_db.outbox",
      "schema.history.internal.kafka.bootstrap.servers": "kafka:29092",
      "schema.history.internal.kafka.topic": "schema-changes.lp-mall",
      "value.converter": "org.apache.kafka.connect.storage.StringConverter",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.id": "id",
      "transforms.outbox.table.field.event.key": "aggregate_id",
      "transforms.outbox.table.field.event.type": "type",
      "transforms.outbox.table.field.event.payload": "payload",
      "transforms.outbox.route.by.field": "aggregate_type",
      "transforms.outbox.route.topic.replacement": "${routedByValue}"
    }
  }'


echo ""
echo "Waiting for connector to start..."
sleep 5

echo "Done! Connector status:"
curl -s http://localhost:8086/connectors/lp-mall-outbox-connector/status | python3 -m json.tool 2>/dev/null || \
curl -s http://localhost:8086/connectors/lp-mall-outbox-connector/status
