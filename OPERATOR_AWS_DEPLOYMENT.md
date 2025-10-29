# Operator Backend - Complete AWS Deployment Script

## Prerequisites
```bash
export AWS_REGION="ap-south-1"
export API_ID="b7qa2tmqhg"
export AWS_ACCOUNT_ID="YOUR_ACCOUNT_ID"
export RDS_ENDPOINT="your-rds-endpoint.rds.amazonaws.com"
export DB_PASSWORD="your-db-password"
```

---

## 1. Database Migration

```bash
# Connect and run migration
mysql -h $RDS_ENDPOINT -u admin -p$DB_PASSWORD campusbus < operator-backend-schema.sql

# Verify
mysql -h $RDS_ENDPOINT -u admin -p$DB_PASSWORD campusbus -e "
SHOW TABLES LIKE '%operator%';
SHOW TABLES LIKE '%trip_assignment%';
SHOW TABLES LIKE '%misconduct%';
DESCRIBE trips;
SELECT * FROM operators;
"
```

---

## 2. S3 Bucket Setup

```bash
# Create bucket
aws s3 mb s3://campusbus-misconduct-photos --region $AWS_REGION

# Set bucket policy
cat > /tmp/s3-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "lambda.amazonaws.com"},
    "Action": ["s3:PutObject", "s3:GetObject"],
    "Resource": "arn:aws:s3:::campusbus-misconduct-photos/*"
  }]
}
EOF

aws s3api put-bucket-policy --bucket campusbus-misconduct-photos \
  --policy file:///tmp/s3-policy.json --region $AWS_REGION

# Enable versioning
aws s3api put-bucket-versioning --bucket campusbus-misconduct-photos \
  --versioning-configuration Status=Enabled --region $AWS_REGION

# Set lifecycle policy (archive after 90 days)
cat > /tmp/lifecycle.json << 'EOF'
{
  "Rules": [{
    "Id": "ArchiveOldPhotos",
    "Status": "Enabled",
    "Transitions": [{
      "Days": 90,
      "StorageClass": "GLACIER"
    }]
  }]
}
EOF

aws s3api put-bucket-lifecycle-configuration --bucket campusbus-misconduct-photos \
  --lifecycle-configuration file:///tmp/lifecycle.json --region $AWS_REGION
```

---

## 3. IoT Core Configuration

```bash
# Create IoT policy
cat > /tmp/iot-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "iot:Publish",
    "Resource": "arn:aws:iot:ap-south-1:*:topic/bus/location/*"
  }]
}
EOF

aws iot create-policy --policy-name LambdaIoTPublishPolicy \
  --policy-document file:///tmp/iot-policy.json --region $AWS_REGION

# Create topic rule
cat > /tmp/iot-rule.json << 'EOF'
{
  "sql": "SELECT * FROM 'bus/location/+'",
  "description": "Broadcast bus GPS locations",
  "actions": [],
  "ruleDisabled": false
}
EOF

aws iot create-topic-rule --rule-name BusLocationBroadcast \
  --topic-rule-payload file:///tmp/iot-rule.json --region $AWS_REGION
```

---

## 4. IAM Role Update

```bash
# Update Lambda execution role
cat > /tmp/operator-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject"],
      "Resource": "arn:aws:s3:::campusbus-misconduct-photos/*"
    },
    {
      "Effect": "Allow",
      "Action": "iot:Publish",
      "Resource": "arn:aws:iot:$AWS_REGION:$AWS_ACCOUNT_ID:topic/bus/location/*"
    },
    {
      "Effect": "Allow",
      "Action": ["rds:*", "ec2:CreateNetworkInterface", "ec2:DescribeNetworkInterfaces", "ec2:DeleteNetworkInterface"],
      "Resource": "*"
    }
  ]
}
EOF

aws iam put-role-policy --role-name CampusBusLambdaRole \
  --policy-name OperatorBackendPolicy \
  --policy-document file:///tmp/operator-policy.json
```

---

## 5. Build and Package

```bash
cd BookingSystemBackend
./mvnw clean package
JAR_PATH="target/booking-system-0.0.1-SNAPSHOT.jar"
```

---

## 6. Deploy Lambda Functions

```bash
# Function 1: Operator Login
aws lambda create-function --function-name OperatorLoginHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.OperatorLoginHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD}" \
  --region $AWS_REGION

# Function 2: Get Operator Trips
aws lambda create-function --function-name GetOperatorTripsHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.GetOperatorTripsHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD}" \
  --region $AWS_REGION

# Function 3: Start Trip
aws lambda create-function --function-name StartTripHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.StartTripHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD}" \
  --region $AWS_REGION

# Function 4: Get Passenger List
aws lambda create-function --function-name GetPassengerListHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.GetPassengerListHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD}" \
  --region $AWS_REGION

# Function 5: Submit Misconduct Report
aws lambda create-function --function-name SubmitMisconductReportHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.SubmitMisconductReportHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD,MISCONDUCT_PHOTOS_BUCKET=campusbus-misconduct-photos}" \
  --region $AWS_REGION

# Function 6: Update GPS Location
aws lambda create-function --function-name UpdateGPSLocationHandler \
  --runtime java17 --role arn:aws:iam::$AWS_ACCOUNT_ID:role/CampusBusLambdaRole \
  --handler com.campusbus.lambda.UpdateGPSLocationHandler::handleRequest \
  --zip-file fileb://$JAR_PATH --timeout 30 --memory-size 512 \
  --environment Variables="{DB_HOST=$RDS_ENDPOINT,DB_NAME=campusbus,DB_USERNAME=admin,DB_PASSWORD=$DB_PASSWORD}" \
  --region $AWS_REGION
```

---

## 7. API Gateway - Create Resources

```bash
# Get root resource ID
ROOT_ID=$(aws apigateway get-resources --rest-api-id $API_ID --region $AWS_REGION \
  --query 'items[?path==`/`].id' --output text)

# Create /operator
OPERATOR_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $ROOT_ID --path-part operator --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/login
LOGIN_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $OPERATOR_ID --path-part login --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/trips
TRIPS_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $OPERATOR_ID --path-part trips --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/trips/start
START_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $TRIPS_ID --path-part start --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/trips/{tripId}
TRIPID_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $TRIPS_ID --path-part '{tripId}' --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/trips/{tripId}/passengers
PASSENGERS_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $TRIPID_ID --path-part passengers --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/reports
REPORTS_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $OPERATOR_ID --path-part reports --region $AWS_REGION \
  --query 'id' --output text)

# Create /operator/gps
GPS_ID=$(aws apigateway create-resource --rest-api-id $API_ID \
  --parent-id $OPERATOR_ID --path-part gps --region $AWS_REGION \
  --query 'id' --output text)

echo "Resource IDs created:"
echo "OPERATOR_ID=$OPERATOR_ID"
echo "LOGIN_ID=$LOGIN_ID"
echo "TRIPS_ID=$TRIPS_ID"
echo "START_ID=$START_ID"
echo "TRIPID_ID=$TRIPID_ID"
echo "PASSENGERS_ID=$PASSENGERS_ID"
echo "REPORTS_ID=$REPORTS_ID"
echo "GPS_ID=$GPS_ID"
```

---

## 8. API Gateway - Create Methods

```bash
# POST /operator/login
aws apigateway put-method --rest-api-id $API_ID --resource-id $LOGIN_ID \
  --http-method POST --authorization-type NONE --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $LOGIN_ID \
  --http-method POST --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:OperatorLoginHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name OperatorLoginHandler \
  --statement-id apigateway-operator-login --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/POST/operator/login" \
  --region $AWS_REGION

# GET /operator/trips
aws apigateway put-method --rest-api-id $API_ID --resource-id $TRIPS_ID \
  --http-method GET --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $TRIPS_ID \
  --http-method GET --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:GetOperatorTripsHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name GetOperatorTripsHandler \
  --statement-id apigateway-operator-trips --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/GET/operator/trips" \
  --region $AWS_REGION

# POST /operator/trips/start
aws apigateway put-method --rest-api-id $API_ID --resource-id $START_ID \
  --http-method POST --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $START_ID \
  --http-method POST --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:StartTripHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name StartTripHandler \
  --statement-id apigateway-start-trip --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/POST/operator/trips/start" \
  --region $AWS_REGION

# GET /operator/trips/{tripId}/passengers
aws apigateway put-method --rest-api-id $API_ID --resource-id $PASSENGERS_ID \
  --http-method GET --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true,method.request.path.tripId=true \
  --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $PASSENGERS_ID \
  --http-method GET --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:GetPassengerListHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name GetPassengerListHandler \
  --statement-id apigateway-passengers --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/GET/operator/trips/*/passengers" \
  --region $AWS_REGION

# POST /operator/reports
aws apigateway put-method --rest-api-id $API_ID --resource-id $REPORTS_ID \
  --http-method POST --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $REPORTS_ID \
  --http-method POST --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:SubmitMisconductReportHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name SubmitMisconductReportHandler \
  --statement-id apigateway-reports --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/POST/operator/reports" \
  --region $AWS_REGION

# POST /operator/gps
aws apigateway put-method --rest-api-id $API_ID --resource-id $GPS_ID \
  --http-method POST --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true --region $AWS_REGION

aws apigateway put-integration --rest-api-id $API_ID --resource-id $GPS_ID \
  --http-method POST --type AWS_PROXY --integration-http-method POST \
  --uri arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:UpdateGPSLocationHandler/invocations \
  --region $AWS_REGION

aws lambda add-permission --function-name UpdateGPSLocationHandler \
  --statement-id apigateway-gps --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$API_ID/*/POST/operator/gps" \
  --region $AWS_REGION
```

---

## 9. Enable CORS

```bash
# Function to enable CORS for a resource
enable_cors() {
  local RESOURCE_ID=$1
  
  aws apigateway put-method --rest-api-id $API_ID --resource-id $RESOURCE_ID \
    --http-method OPTIONS --authorization-type NONE --region $AWS_REGION
  
  aws apigateway put-integration --rest-api-id $API_ID --resource-id $RESOURCE_ID \
    --http-method OPTIONS --type MOCK \
    --request-templates '{"application/json": "{\"statusCode\": 200}"}' --region $AWS_REGION
  
  aws apigateway put-method-response --rest-api-id $API_ID --resource-id $RESOURCE_ID \
    --http-method OPTIONS --status-code 200 \
    --response-parameters method.response.header.Access-Control-Allow-Headers=true,method.response.header.Access-Control-Allow-Methods=true,method.response.header.Access-Control-Allow-Origin=true \
    --region $AWS_REGION
  
  aws apigateway put-integration-response --rest-api-id $API_ID --resource-id $RESOURCE_ID \
    --http-method OPTIONS --status-code 200 \
    --response-parameters method.response.header.Access-Control-Allow-Headers="'Content-Type,Authorization'",method.response.header.Access-Control-Allow-Methods="'GET,POST,OPTIONS'",method.response.header.Access-Control-Allow-Origin="'*'" \
    --region $AWS_REGION
}

# Enable CORS for all endpoints
enable_cors $LOGIN_ID
enable_cors $TRIPS_ID
enable_cors $START_ID
enable_cors $PASSENGERS_ID
enable_cors $REPORTS_ID
enable_cors $GPS_ID
```

---

## 10. Deploy API Gateway

```bash
aws apigateway create-deployment --rest-api-id $API_ID \
  --stage-name dev --description "Operator backend deployment" \
  --region $AWS_REGION

echo "✅ Deployment Complete!"
echo "Base URL: https://$API_ID.execute-api.$AWS_REGION.amazonaws.com/dev"
```

---

## 11. Test Endpoints

```bash
BASE_URL="https://$API_ID.execute-api.$AWS_REGION.amazonaws.com/dev"

# Test 1: Operator Login
echo "Testing operator login..."
LOGIN_RESPONSE=$(curl -s -X POST $BASE_URL/operator/login \
  -H "Content-Type: application/json" \
  -d '{"employeeId":"op101","password":"buspass"}')
echo $LOGIN_RESPONSE

# Extract token
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')
echo "Token: $TOKEN"

# Test 2: Get Operator Trips
echo "Testing get operator trips..."
curl -s -X GET $BASE_URL/operator/trips \
  -H "Authorization: Bearer $TOKEN" | jq

# Test 3: Start Trip
echo "Testing start trip..."
curl -s -X POST $BASE_URL/operator/trips/start \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tripId":"TRIP_WD_0830_1_C2C"}' | jq

# Test 4: Get Passenger List
echo "Testing get passenger list..."
curl -s -X GET $BASE_URL/operator/trips/TRIP_WD_0830_1_C2C/passengers \
  -H "Authorization: Bearer $TOKEN" | jq

# Test 5: Submit Misconduct Report
echo "Testing submit misconduct report..."
curl -s -X POST $BASE_URL/operator/reports \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "studentId":"S12AB34CD",
    "tripId":"TRIP_WD_0830_1_C2C",
    "reason":"Misbehavior",
    "comments":"Test report"
  }' | jq

# Test 6: Update GPS Location
echo "Testing GPS update..."
curl -s -X POST $BASE_URL/operator/gps \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "tripId":"TRIP_WD_0830_1_C2C",
    "latitude":26.9124,
    "longitude":75.5373
  }' | jq
```

---

## 12. CloudWatch Logs Setup

```bash
# Create log groups
for FUNCTION in OperatorLoginHandler GetOperatorTripsHandler StartTripHandler \
                GetPassengerListHandler SubmitMisconductReportHandler UpdateGPSLocationHandler
do
  aws logs create-log-group --log-group-name /aws/lambda/$FUNCTION --region $AWS_REGION
  aws logs put-retention-policy --log-group-name /aws/lambda/$FUNCTION \
    --retention-in-days 7 --region $AWS_REGION
done
```

---

## Verification Checklist

- [ ] Database tables created (operators, trip_assignments, misconduct_reports)
- [ ] S3 bucket created and accessible
- [ ] IoT Core policy and topic rule configured
- [ ] IAM role updated with S3 and IoT permissions
- [ ] 6 Lambda functions deployed successfully
- [ ] Environment variables set for all functions
- [ ] 7 API Gateway resources created
- [ ] 7 API Gateway methods configured
- [ ] Lambda permissions granted to API Gateway
- [ ] CORS enabled for all endpoints
- [ ] API deployed to dev stage
- [ ] Operator login test successful
- [ ] All endpoint tests passing
- [ ] CloudWatch logs accessible

---

## Rollback Commands

```bash
# Delete Lambda functions
for FUNCTION in OperatorLoginHandler GetOperatorTripsHandler StartTripHandler \
                GetPassengerListHandler SubmitMisconductReportHandler UpdateGPSLocationHandler
do
  aws lambda delete-function --function-name $FUNCTION --region $AWS_REGION
done

# Delete S3 bucket
aws s3 rb s3://campusbus-misconduct-photos --force --region $AWS_REGION

# Delete IoT resources
aws iot delete-topic-rule --rule-name BusLocationBroadcast --region $AWS_REGION
aws iot delete-policy --policy-name LambdaIoTPublishPolicy --region $AWS_REGION

# Rollback database
mysql -h $RDS_ENDPOINT -u admin -p$DB_PASSWORD campusbus -e "
DROP TABLE IF EXISTS misconduct_reports;
DROP TABLE IF EXISTS trip_assignments;
DROP TABLE IF EXISTS operators;
ALTER TABLE trips DROP COLUMN IF EXISTS assigned_operator_id;
ALTER TABLE trips DROP COLUMN IF EXISTS bus_number;
"
```

---

## Cost Estimate

- **Lambda**: 6 functions × $0.20/million requests = ~$1.20/month
- **API Gateway**: $3.50/million requests = ~$0.35/month (10K requests)
- **S3**: $0.023/GB = ~$0.50/month (20GB photos)
- **IoT Core**: $1.00/million messages = ~$0.10/month (100K GPS updates)
- **RDS**: No additional cost (existing instance)

**Total Additional Cost**: ~$2.15/month

---

## Support & Troubleshooting

### Common Issues

**403 Forbidden**: Re-run Lambda permission commands
**502 Bad Gateway**: Check Lambda logs in CloudWatch
**CORS errors**: Verify OPTIONS method configuration
**Database connection timeout**: Check VPC security groups

### Useful Commands

```bash
# View Lambda logs
aws logs tail /aws/lambda/OperatorLoginHandler --follow --region $AWS_REGION

# Test Lambda directly
aws lambda invoke --function-name OperatorLoginHandler \
  --payload '{"body":"{\"employeeId\":\"op101\",\"password\":\"buspass\"}"}' \
  response.json --region $AWS_REGION

# Check API Gateway resources
aws apigateway get-resources --rest-api-id $API_ID --region $AWS_REGION

# View S3 bucket contents
aws s3 ls s3://campusbus-misconduct-photos/ --recursive
```
