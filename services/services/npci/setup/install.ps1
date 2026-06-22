# Install and setup script for NPCI Service (VM-2)
Write-Host "Setting up NPCI Service on Windows"

# Build
cd ..
mvn clean package -DskipTests

Write-Host "Setup complete. Start PostgreSQL manually and run create-db.sql, then run 'java -jar target\npci-service-0.0.1-SNAPSHOT.jar' to start."
