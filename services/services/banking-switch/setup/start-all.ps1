Write-Host "Starting Banking Switch Services..."

Start-Process "java" -ArgumentList "-jar", "../npci-request-listener/target/npci-request-listener-1.0.0-SNAPSHOT.jar"
Start-Process "java" -ArgumentList "-jar", "../orchestrator/target/orchestrator-1.0.0-SNAPSHOT.jar"
Start-Process "java" -ArgumentList "-jar", "../cbs-adapter/target/cbs-adapter-1.0.0-SNAPSHOT.jar"
Start-Process "java" -ArgumentList "-jar", "../npci-response-adapter/target/npci-response-adapter-1.0.0-SNAPSHOT.jar"

Write-Host "Services started in new windows."
