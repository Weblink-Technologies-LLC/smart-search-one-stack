FROM openjdk:21-slim-bullseye 

WORKDIR /search-admin

COPY Search-Admin/target/search-admin-3.0.6-SNAPSHOT.jar .

COPY Search-Admin/target/.env .

# Search-API Microservices
WORKDIR /search-api

COPY SmartSearchAPI/target/smart-search-api-3.0.6-SNAPSHOT.jar .

COPY SmartSearchAPI/target/.env .

# Smart-Search-Utilservices
WORKDIR /smart-search-util

COPY SmartSearchUtilservice/target/smart-search-util-3.0.6-SNAPSHOT.jar .

COPY SmartSearchUtilservice/target/.env .env

# EXECUTE
WORKDIR /
COPY ss-api.sh .
RUN chmod +x ss-api.sh

CMD ["./ss-api.sh"]
