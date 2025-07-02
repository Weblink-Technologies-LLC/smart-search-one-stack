#!/bin/bash


set -e  # Exit on any error

echo "🚀 Starting Smart Search One Stack..."
echo "This will start Elasticsearch, MongoDB, Redis, and Smart Search services"
echo ""

echo "📡 Creating Docker network..."
docker network create smartsearch-net 2>/dev/null || true

echo "🔍 Starting Elasticsearch..."
docker run -d --name elasticsearch --network smartsearch-net \
  -e "discovery.type=single-node" \
  -e "ELASTIC_PASSWORD=Cu5BAieKx8cpD4q" \
  -e "xpack.security.enabled=true" \
  -e "xpack.security.http.ssl.enabled=false" \
  -p 9200:9200 \
  docker.elastic.co/elasticsearch/elasticsearch:8.14.3

echo "🗄️  Starting MongoDB..."
docker run -d --name mongodb --network smartsearch-net \
  -e "MONGO_INITDB_ROOT_USERNAME=admin" \
  -e "MONGO_INITDB_ROOT_PASSWORD=adminpass" \
  -e "MONGO_INITDB_DATABASE=ss-test-onestack" \
  -p 27018:27017 \
  mongo:7.0

echo "⚡ Starting Redis..."
docker run -d --name redis --network smartsearch-net \
  -p 6380:6379 \
  redis:7.2-alpine

echo "⏳ Waiting for services to start (30 seconds)..."
sleep 30

echo "🎯 Starting Smart Search application..."
docker run -d --name smartsearch --network smartsearch-net \
  -e "MONGODB_URI=mongodb://admin:adminpass@mongodb:27017/ss-test-onestack?authSource=admin&retryWrites=true&w=majority" \
  -e "ELASTIC_PASSWORD=Cu5BAieKx8cpD4q" \
  -e "INTERNAL_SEARCH_ENGINE_URL=http://elasticsearch:9200" \
  -e "INTERNAL_SEARCH_ENGINE_USERNAME=elastic" \
  -e "INTERNAL_SEARCH_ENGINE_PASSWORD=Cu5BAieKx8cpD4q" \
  -p 9080:9080 -p 9081:9081 -p 9085:9085 \
  weblinktechs2021/ss-one-stack:secure-latest

echo ""
echo "✅ Smart Search One Stack is starting up!"
echo ""
echo "📊 Service Ports:"
echo "   Smart Search Admin: 9080"
echo "   Smart Search API:   9081"
echo "   Smart Search Utils: 9085"
echo "   Elasticsearch:      9200"
echo "   MongoDB:            27018"
echo "   Redis:              6380"
echo ""
echo "🔧 Useful Commands:"
echo "   View logs:          docker logs smartsearch"
echo "   Stop everything:    ./stop-smartsearch.sh"
echo "   Check status:       docker ps"
echo ""
echo "⏰ Please wait 1-2 minutes for all services to fully initialize..."
echo "📋 Access the application at the ports listed above"
