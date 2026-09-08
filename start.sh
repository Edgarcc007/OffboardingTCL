#!/bin/bash

echo "🚀 Iniciando OffboardingTCL..."

# Asegurar que Docker esté corriendo
sudo service docker start 2>/dev/null

# Levantar los contenedores
docker compose up -d

# Esperar a que la app esté lista
echo "⏳ Esperando a que la app arranque..."
until curl -s http://localhost:8080 > /dev/null 2>&1; do
    sleep 2
done

echo "✅ App lista en http://localhost:8080"
echo "📊 Portainer en http://localhost:9000"

# Abrir en el navegador de Windows
cmd.exe /c start http://localhost:8080

