#!/bin/sh
ADMIN=$(curl -s -X POST http://api-gateway:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"admin","password":"dev123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s -X POST http://api-gateway:8080/api/v1/admin/refunds/16/approve -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' -d '{"adminNote":"Approved manually"}'
