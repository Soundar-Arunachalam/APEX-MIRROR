Write-Host "Building Banking Switch Microservices..."
Set-Location ..
mvn clean compile
mvn clean install -DskipTests
Write-Host "Build complete."
