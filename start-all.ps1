# Script to run the complete RH Platform Environment (Infrastructure + Microservices + Frontend)
# Ensure Docker Desktop is running before executing this!

Write-Host "======== [ 1 / 3 ] Démarrage de l'infrastructure Docker... ========" -ForegroundColor Cyan
docker-compose -f docker-compose.infra.yml up -d
docker-compose -f docker-compose.kafka.yml up -d

Write-Host "Attente de 10 secondes pour l'initialisation de Kafka et Mailhog..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host "======== [ 2 / 3 ] Démarrage des Microservices (JDK 21) ========" -ForegroundColor Cyan
# Set JAVA_HOME specifically for Maven execution
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"

Write-Host "1. Eureka Server..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Title", "Eureka Server", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn spring-boot:run -pl eureka-server"
Start-Sleep -Seconds 8 # Give registry time to start

Write-Host "2. Gateway..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Title", "Gateway", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn spring-boot:run -pl gateway"
Start-Sleep -Seconds 2

Write-Host "3. Identite-Acces..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Title", "Identite Acces", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn spring-boot:run -pl svc-identite-acces"
Start-Sleep -Seconds 2

Write-Host "4. Referentiel-RH..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Title", "Referentiel RH", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn spring-boot:run -pl svc-referentiel-rh"
Start-Sleep -Seconds 2

Write-Host "5. Notification..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Title", "Notification", "-Command", "`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; mvn spring-boot:run -pl svc-notification"

Write-Host "======== [ 3 / 3 ] Démarrage du Frontend web ========" -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Title", "React Admin Web", "-Command", "cd rh-admin-web; npm run dev"

Write-Host "Tout a été lancé avec succès ! Vérifiez les nouvelles fenêtres bleues pour suivre les logs de chaque service." -ForegroundColor Green
