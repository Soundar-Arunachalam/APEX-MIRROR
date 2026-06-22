#!/bin/bash
echo "Starting Banking Switch Services..."

java -jar ../npci-request-listener/target/npci-request-listener-1.0.0-SNAPSHOT.jar &
java -jar ../orchestrator/target/orchestrator-1.0.0-SNAPSHOT.jar &
java -jar ../cbs-adapter/target/cbs-adapter-1.0.0-SNAPSHOT.jar &
java -jar ../npci-response-adapter/target/npci-response-adapter-1.0.0-SNAPSHOT.jar &

echo "All services started."
wait
