#!/bin/bash

set -e

echo "🛑 Stopping Smart Search One Stack (Secure Mode)"
echo "================================================"

echo "🔧 Loading environment variables from secrets..."
if [ -f secrets/elastic_password.txt ]; then
    export ELASTIC_PASSWORD=$(cat secrets/elastic_password.txt)
fi

if [ -f secrets/jwt_secret.txt ]; then
    export JWT_SECRET_KEY=$(cat secrets/jwt_secret.txt)
fi

if [ -f secrets/password_encryption_key.txt ]; then
    export PASSWORD_ENCRYPTION_KEY=$(cat secrets/password_encryption_key.txt)
fi

if [ -f secrets/aws_secret_key.txt ]; then
    export AWS_CREDENTIALS_SECRET_KEY=$(cat secrets/aws_secret_key.txt)
fi

export AWS_CREDENTIALS_ACCESS_KEY="demo-access-key-for-testing-only"
export SMARTSEARCH_ENC_SECRET_KEY="95bee83ac83f3193fe81b7bc75070fc4"

echo "🧹 Stopping and removing containers..."
docker compose -f docker-compose.secure.yaml down --remove-orphans

echo "🧹 Removing any remaining containers..."
docker rm -f mongodb-secure redis-secure elasticsearch-secure smartsearch-secure 2>/dev/null || true

echo "🌐 Removing secure networks..."
docker network rm smartsearch-frontend smartsearch-backend 2>/dev/null || true

echo "✅ Smart Search One Stack stopped successfully"
echo "   All containers removed"
echo "   Networks cleaned up"
echo "   Data volumes preserved"
