#!/bin/bash
echo "Building Banking Switch Microservices..."
cd ..
mvn clean compile
mvn clean install -DskipTests
echo "Build complete."
