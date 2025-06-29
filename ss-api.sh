#!/bin/bash
source /search-admin/.env
java -jar /search-admin/search-admin-3.0.7-SNAPSHOT.jar > /tmp/ss-admin.log &
source /search-api/.env
java -jar /search-api/smart-search-api-3.0.7-SNAPSHOT.jar > /tmp/ss-api.log &
source /smart-search-util/.env
java -jar /smart-search-util/smart-search-util-3.0.7-SNAPSHOT.jar > /tmp/ss-utils.log &
wait
