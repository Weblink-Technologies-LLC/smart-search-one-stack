#!/bin/bash


set -e

echo "🔒 Starting Smart Search One Stack (Secure Mode)"
echo "================================================"

mkdir -p secrets

if [ ! -f secrets/jwt_secret.txt ]; then
    echo "🔑 Generating JWT secret..."
    openssl rand -base64 32 > secrets/jwt_secret.txt
fi

if [ ! -f secrets/password_encryption_key.txt ]; then
    echo "🔑 Generating password encryption key..."
    openssl rand -base64 32 > secrets/password_encryption_key.txt
fi

if [ ! -f secrets/aws_secret_key.txt ]; then
    echo "🔑 Generating AWS secret key..."
    echo "demo-aws-secret-key-$(openssl rand -hex 16)" > secrets/aws_secret_key.txt
fi

if [ ! -f secrets/license_product_data.txt ]; then
    echo "🔑 Generating license data..."
    echo "demo-license-data-$(date +%s)" > secrets/license_product_data.txt
fi

if [ ! -f secrets/mongodb_root_password.txt ]; then
    echo "🔑 Generating MongoDB root password..."
    openssl rand -base64 32 > secrets/mongodb_root_password.txt
fi

if [ ! -f secrets/mongodb_app_password.txt ]; then
    echo "🔑 Generating MongoDB app password..."
    openssl rand -base64 32 > secrets/mongodb_app_password.txt
fi

if [ ! -f secrets/elastic_password.txt ]; then
    echo "🔑 Generating Elasticsearch password..."
    openssl rand -base64 32 > secrets/elastic_password.txt
fi

echo "✅ All secrets configured"

echo "🧹 Cleaning up existing containers..."
docker compose -f docker-compose.secure.yaml down --remove-orphans 2>/dev/null || true

docker rm -f mongodb-secure redis-secure elasticsearch-secure smartsearch-secure 2>/dev/null || true

echo "🌐 Creating secure internal networks..."
docker network create smartsearch-frontend 2>/dev/null || true
docker network create smartsearch-backend --internal 2>/dev/null || true

echo "🔧 Setting environment variables from secrets..."
export ELASTIC_PASSWORD=$(cat secrets/elastic_password.txt)
export MONGODB_ROOT_PASSWORD=$(cat secrets/mongodb_root_password.txt)
export JWT_SECRET_KEY=$(cat secrets/jwt_secret.txt)
export PASSWORD_ENCRYPTION_KEY=$(cat secrets/password_encryption_key.txt)
export AWS_CREDENTIALS_SECRET_KEY=$(cat secrets/aws_secret_key.txt)
export AWS_CREDENTIALS_ACCESS_KEY="demo-access-key-for-testing-only"
export LICENSE_PRODUCT_DATA=$(cat secrets/license_product_data.txt)
export SMARTSEARCH_ENC_SECRET_KEY="95bee83ac83f3193fe81b7bc75070fc4"

echo "🚀 Starting services..."
echo "   📊 MongoDB (internal-only, no external access)"
echo "   🔍 Elasticsearch (internal-only, no external access)"  
echo "   💾 Redis (internal-only, no external access)"
echo "   🎯 Smart Search Application (ports 9080, 9081, 9085)"

docker compose -f docker-compose.secure.yaml up -d

echo ""
echo "⏳ Waiting for services to start..."
sleep 30

echo ""
echo "🎉 Smart Search One Stack is starting!"
echo "================================================"
echo "🌐 Admin Interface:     http://localhost:9080/auth/"
echo "📚 API Documentation:   http://localhost:9081/swagger-ui.html"
echo "🔍 Utility Service:     http://localhost:9085/actuator/health"
echo ""
echo "🔒 Security Features:"
echo "   ✅ No external database ports exposed"
echo "   ✅ Internal-only Docker networks"
echo "   ✅ Container hardening enabled"
echo "   ✅ Auto-configured environment variables"
echo "   ✅ Read-only container filesystems"
echo "   ✅ Dropped container capabilities"
echo ""
echo "📋 To view logs: docker compose -f docker-compose.secure.yaml logs -f"
echo "🛑 To stop:      ./stop-smartsearch-secure.sh"
echo ""
echo "⚠️  Note: Databases are completely isolated from external access"
echo "   Only the Smart Search application can connect to them internally"
echo ""
echo "⏳ Waiting for Smart Search admin interface to be ready..."
sleep 10

for i in {1..12}; do
    if curl -s -o /dev/null -w "%{http_code}" http://localhost:9080/auth/ | grep -q "200\|302\|404"; then
        echo "✅ Smart Search admin interface is ready!"
        echo "🌐 Opening http://localhost:9080/auth/ in your browser..."
        
        if command -v xdg-open > /dev/null; then
            xdg-open http://localhost:9080/auth/ 2>/dev/null &
        elif command -v open > /dev/null; then
            open http://localhost:9080/auth/ 2>/dev/null &
        elif command -v google-chrome > /dev/null; then
            google-chrome http://localhost:9080/auth/ 2>/dev/null &
        elif command -v firefox > /dev/null; then
            firefox http://localhost:9080/auth/ 2>/dev/null &
        else
            echo "⚠️  Could not auto-open browser. Please manually visit: http://localhost:9080/auth/"
        fi
        break
    else
        echo "⏳ Waiting for services to be ready... (attempt $i/12)"
        sleep 10
    fi
done

if [ $i -eq 12 ]; then
    echo "⚠️  Services may still be starting. Please check: http://localhost:9080/auth/"
fi
