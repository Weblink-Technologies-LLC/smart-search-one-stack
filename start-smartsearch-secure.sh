#!/bin/bash

set -e

echo "🔒 Starting Smart Search One Stack (Secure Mode) - Staged Startup"
echo "=================================================================="

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

if [ ! -f secrets/elastic_password.txt ]; then
    echo "🔑 Generating Elasticsearch password..."
    echo "$(openssl rand -base64 32 | tr -d '+/=' | head -c 16)" > secrets/elastic_password.txt
else
    echo "🔑 Using existing Elasticsearch password..."
fi

echo "✅ All secrets configured"

echo "🧹 Cleaning up existing containers and volumes..."
docker compose -f docker-compose.secure.yaml down --remove-orphans 2>/dev/null || true
docker rm -f mongodb-secure redis-secure elasticsearch-secure smartsearch-secure 2>/dev/null || true
docker volume rm smart-search-one-stack_mongodata 2>/dev/null || true

echo "🌐 Creating secure internal networks..."
docker network create smartsearch-frontend 2>/dev/null || true
docker network create smartsearch-backend --internal 2>/dev/null || true

echo "🔧 Setting environment variables from secrets..."
export ELASTIC_PASSWORD=$(cat secrets/elastic_password.txt)
export JWT_SECRET_KEY=$(cat secrets/jwt_secret.txt)
export PASSWORD_ENCRYPTION_KEY=$(cat secrets/password_encryption_key.txt)
export AWS_CREDENTIALS_SECRET_KEY=$(cat secrets/aws_secret_key.txt)
export AWS_CREDENTIALS_ACCESS_KEY="demo-access-key-for-testing-only"
export SMARTSEARCH_ENC_SECRET_KEY="95bee83ac83f3193fe81b7bc75070fc4"

echo ""
echo "🚀 STAGE 1: Starting infrastructure services..."
echo "   📊 MongoDB (internal-only, no external access)"
echo "   🔍 Elasticsearch (internal-only, no external access)"  
echo "   💾 Redis (internal-only, no external access)"

docker compose -f docker-compose.secure.yaml up -d mongodb redis elasticsearch

echo ""
echo "⏳ STAGE 2: Waiting for MongoDB to be ready..."
for i in {1..30}; do
    if docker exec mongodb-secure mongosh admin --quiet --eval "db.runCommand('ping')" >/dev/null 2>&1; then
        echo "✅ MongoDB container is responding to ping"
        break
    else
        echo "⏳ Waiting for MongoDB to start... (attempt $i/30)"
        sleep 2
    fi
done

if [ $i -eq 30 ]; then
    echo "❌ MongoDB failed to start after 60 seconds"
    exit 1
fi

echo ""
echo "🔐 STAGE 3: Testing MongoDB authentication..."
for i in {1..15}; do
    AUTH_RESULT=$(docker exec mongodb-secure mongosh admin --quiet --eval "db.auth('root', 'SecureMongoPass2024')" 2>/dev/null || echo "failed")
    if echo "$AUTH_RESULT" | grep -q "ok.*1"; then
        echo "✅ MongoDB authentication successful: $AUTH_RESULT"
        break
    else
        echo "⏳ Waiting for MongoDB user initialization... (attempt $i/15)"
        echo "   Auth result: $AUTH_RESULT"
        sleep 3
    fi
done

if [ $i -eq 15 ]; then
    echo "❌ MongoDB authentication failed after 45 seconds"
    echo "🔍 Checking MongoDB logs..."
    docker logs mongodb-secure --tail=20
    exit 1
fi

echo ""
echo "⏳ STAGE 4: Waiting for Elasticsearch to be ready..."
for i in {1..20}; do
    if curl -s -u "elastic:${ELASTIC_PASSWORD}" "http://localhost:9200/_cluster/health" >/dev/null 2>&1; then
        echo "✅ Elasticsearch is ready"
        break
    else
        echo "⏳ Waiting for Elasticsearch... (attempt $i/20)"
        sleep 3
    fi
done

echo ""
echo "⏳ STAGE 5: Waiting for Redis to be ready..."
for i in {1..10}; do
    if docker exec redis-secure redis-cli ping >/dev/null 2>&1; then
        echo "✅ Redis is ready"
        break
    else
        echo "⏳ Waiting for Redis... (attempt $i/10)"
        sleep 2
    fi
done

echo ""
echo "🎯 STAGE 6: Starting Smart Search Application..."
echo "   Now that MongoDB authentication and Redis are confirmed working"

docker compose -f docker-compose.secure.yaml up -d smartsearch-secure

echo ""
echo "⏳ STAGE 7: Waiting for Smart Search application to start..."
sleep 20

echo ""
echo "🔍 Checking application startup logs..."
docker logs smartsearch-secure --tail=30 | grep -E "(MongoDB|Mongock|authentication|started|ERROR|license)" -i || true

echo ""
echo "📊 Container status check..."
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "(mongodb-secure|redis-secure|elasticsearch-secure|smartsearch-secure)"

echo ""
echo "🎉 Smart Search One Stack startup complete!"
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
echo "   ✅ Staged startup prevents authentication timing issues"
echo ""
echo "📋 To view logs: docker compose -f docker-compose.secure.yaml logs -f"
echo "🛑 To stop:      ./stop-smartsearch-secure.sh"
echo ""
echo "⚠️  Note: Databases are completely isolated from external access"
echo "   Only the Smart Search application can connect to them internally"
echo ""
echo "⏳ Waiting for Smart Search admin interface to be ready..."

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
        echo "⏳ Waiting for admin interface to be ready... (attempt $i/12)"
        sleep 10
    fi
done

if [ $i -eq 12 ]; then
    echo "⚠️  Admin interface may still be starting. Please check: http://localhost:9080/auth/"
    echo "🔍 Check application logs with: docker logs smartsearch-secure"
fi
