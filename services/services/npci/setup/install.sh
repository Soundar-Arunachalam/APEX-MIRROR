#!/bin/bash
# Install and setup script for NPCI Service (VM-2)
sudo apt update
sudo apt install -y openjdk-17-jdk maven postgresql
# Run DB setup
sudo -u postgres psql -f create-db.sql
# Build
cd ..
mvn clean package -DskipTests
echo "Setup complete. Run java -jar target/npci-service-0.0.1-SNAPSHOT.jar to start."
