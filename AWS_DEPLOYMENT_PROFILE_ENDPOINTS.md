# AWS Deployment Guide - Profile & Booking History Endpoints

## Overview
This guide covers deploying the new profile and booking history endpoints to AWS API Gateway.

## Prerequisites
- AWS CLI configured with appropriate credentials
- Lambda functions already deployed:
  - `GetUserProfileHandler`
  - `GetStudentBookingsHandler`
- API Gateway REST API created (ID: b7qa2tmqhg)

## Step 1: Create API Gateway Resources

### 1.1 Create /profile Resource
```bash
# Get the root resource ID
ROOT_ID=$(aws apigateway get-resources \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --query 'items[?path==`/`].id' \
  --output text)

# Create /profile resource
PROFILE_RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --parent-id $ROOT_ID \
  --path-part profile \
  --query 'id' \
  --output text)

echo "Profile Resource ID: $PROFILE_RESOURCE_ID"
```

### 1.2 Create /bookings/history Resource
```bash
# Get /bookings resource ID (should already exist)
BOOKINGS_RESOURCE_ID=$(aws apigateway get-resources \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --query 'items[?pathPart==`bookings`].id' \
  --output text)

# Create /bookings/history resource
HISTORY_RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --parent-id $BOOKINGS_RESOURCE_ID \
  --path-part history \
  --query 'id' \
  --output text)

echo "History Resource ID: $HISTORY_RESOURCE_ID"
```

## Step 2: Create GET Methods

### 2.1 Create GET Method for /profile
```bash
# Create GET method
aws apigateway put-method \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method GET \
  --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true

# Get Lambda function ARN
PROFILE_LAMBDA_ARN=$(aws lambda get-function \
  --function-name GetUserProfileHandler \
  --region ap-south-1 \
  --query 'Configuration.FunctionArn' \
  --output text)

# Create Lambda integration
aws apigateway put-integration \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method GET \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri arn:aws:apigateway:ap-south-1:lambda:path/2015-03-31/functions/$PROFILE_LAMBDA_ARN/invocations

# Grant API Gateway permission to invoke Lambda
aws lambda add-permission \
  --function-name GetUserProfileHandler \
  --statement-id apigateway-profile-get \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:ap-south-1:*:b7qa2tmqhg/*/GET/profile" \
  --region ap-south-1
```

### 2.2 Create GET Method for /bookings/history
```bash
# Create GET method
aws apigateway put-method \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method GET \
  --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true

# Get Lambda function ARN
HISTORY_LAMBDA_ARN=$(aws lambda get-function \
  --function-name GetStudentBookingsHandler \
  --region ap-south-1 \
  --query 'Configuration.FunctionArn' \
  --output text)

# Create Lambda integration
aws apigateway put-integration \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method GET \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri arn:aws:apigateway:ap-south-1:lambda:path/2015-03-31/functions/$HISTORY_LAMBDA_ARN/invocations

# Grant API Gateway permission to invoke Lambda
aws lambda add-permission \
  --function-name GetStudentBookingsHandler \
  --statement-id apigateway-history-get \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:ap-south-1:*:b7qa2tmqhg/*/GET/bookings/history" \
  --region ap-south-1
```

## Step 3: Enable CORS

### 3.1 Enable CORS for /profile
```bash
# Create OPTIONS method
aws apigateway put-method \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method OPTIONS \
  --authorization-type NONE

# Create mock integration for OPTIONS
aws apigateway put-integration \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method OPTIONS \
  --type MOCK \
  --request-templates '{"application/json": "{\"statusCode\": 200}"}'

# Create method response
aws apigateway put-method-response \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers=true,\
method.response.header.Access-Control-Allow-Methods=true,\
method.response.header.Access-Control-Allow-Origin=true

# Create integration response
aws apigateway put-integration-response \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers="'Content-Type,Authorization'",\
method.response.header.Access-Control-Allow-Methods="'GET,OPTIONS'",\
method.response.header.Access-Control-Allow-Origin="'*'"
```

### 3.2 Enable CORS for /bookings/history
```bash
# Create OPTIONS method
aws apigateway put-method \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method OPTIONS \
  --authorization-type NONE

# Create mock integration for OPTIONS
aws apigateway put-integration \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method OPTIONS \
  --type MOCK \
  --request-templates '{"application/json": "{\"statusCode\": 200}"}'

# Create method response
aws apigateway put-method-response \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers=true,\
method.response.header.Access-Control-Allow-Methods=true,\
method.response.header.Access-Control-Allow-Origin=true

# Create integration response
aws apigateway put-integration-response \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers="'Content-Type,Authorization'",\
method.response.header.Access-Control-Allow-Methods="'GET,OPTIONS'",\
method.response.header.Access-Control-Allow-Origin="'*'"
```

## Step 4: Deploy to Stage

```bash
# Deploy to dev stage
aws apigateway create-deployment \
  --rest-api-id b7qa2tmqhg \
  --region ap-south-1 \
  --stage-name dev \
  --description "Added profile and booking history endpoints"

echo "Deployment complete!"
echo "Profile endpoint: https://b7qa2tmqhg.execute-api.ap-south-1.amazonaws.com/dev/profile"
echo "History endpoint: https://b7qa2tmqhg.execute-api.ap-south-1.amazonaws.com/dev/bookings/history"
```

## Step 5: Test Endpoints

### Test Profile Endpoint
```bash
# Replace YOUR_AUTH_TOKEN with actual token
curl -X GET \
  https://b7qa2tmqhg.execute-api.ap-south-1.amazonaws.com/dev/profile \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN" \
  -H "Content-Type: application/json"
```

### Test Booking History Endpoint
```bash
# Replace YOUR_AUTH_TOKEN with actual token
curl -X GET \
  https://b7qa2tmqhg.execute-api.ap-south-1.amazonaws.com/dev/bookings/history \
  -H "Authorization: Bearer YOUR_AUTH_TOKEN" \
  -H "Content-Type: application/json"
```

## Verification Checklist

- [ ] Profile resource created at `/profile`
- [ ] History resource created at `/bookings/history`
- [ ] GET methods configured for both endpoints
- [ ] Lambda integrations working (AWS_PROXY type)
- [ ] Lambda permissions granted to API Gateway
- [ ] CORS enabled for both endpoints
- [ ] Deployment successful to dev stage
- [ ] Test calls return expected data
- [ ] Authorization header validation working
- [ ] Error responses formatted correctly

## Troubleshooting

### Issue: 403 Forbidden
**Solution**: Check Lambda permissions. Re-run the `add-permission` commands.

### Issue: 502 Bad Gateway
**Solution**: Check Lambda function logs in CloudWatch. Verify Lambda is returning proper API Gateway proxy response format.

### Issue: CORS errors in browser
**Solution**: Verify OPTIONS method is configured correctly with proper response headers.

### Issue: 401 Unauthorized
**Solution**: Verify Authorization header is being passed and token validation is working in Lambda.

## Cost Estimate

- API Gateway: $3.50 per million requests
- Lambda invocations: Included in existing Lambda costs
- Data transfer: Minimal (< $0.01/month)

**Total additional cost**: ~$0.10/month for 1000 profile/history requests
