#!/bin/bash


echo "🛑 Stopping Smart Search One Stack (Secure Mode)"
echo "================================================"

docker compose -f docker-compose.secure.yaml down --remove-orphans

docker rm -f mongodb-secure redis-secure elasticsearch-secure smartsearch-secure 2>/dev/null || true

docker network rm smartsearch-frontend smartsearch-backend 2>/dev/null || true

echo "✅ Smart Search One Stack stopped successfully"
echo "   All containers removed"
echo "   Networks cleaned up"
echo "   Data volumes preserved"
