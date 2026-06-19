#!/bin/sh
BUYER=$(curl -s -X POST http://api-gateway:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"credential":"minhhoa","password":"dev123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s http://api-gateway:8080/api/v1/orders/304/refunds -H "Authorization: Bearer $BUYER"
