#!/bin/bash


echo "🛑 Stopping Smart Search One Stack..."
echo ""

echo "⏹️  Stopping containers..."
docker stop smartsearch elasticsearch mongodb redis 2>/dev/null || true

echo "🗑️  Removing containers..."
docker rm smartsearch elasticsearch mongodb redis 2>/dev/null || true

echo "📡 Removing Docker network..."
docker network rm smartsearch-net 2>/dev/null || true

echo ""
echo "✅ Smart Search One Stack has been completely stopped and cleaned up!"
echo ""
echo "🔄 To start again, run: ./start-smartsearch.sh"
