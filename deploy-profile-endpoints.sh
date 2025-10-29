#!/bin/bash

# AWS Deployment Script for Profile & Booking History Endpoints
# This script automates the creation of API Gateway endpoints

set -e

API_ID="b7qa2tmqhg"
REGION="ap-south-1"

echo "=========================================="
echo "Deploying Profile & Booking History Endpoints"
echo "=========================================="

# Get root resource ID
echo "Getting root resource ID..."
ROOT_ID=$(aws apigateway get-resources \
  --rest-api-id $API_ID \
  --region $REGION \
  --query 'items[?path==`/`].id' \
  --output text)
echo "Root ID: $ROOT_ID"

# Create /profile resource
echo "Creating /profile resource..."
PROFILE_RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id $API_ID \
  --region $REGION \
  --parent-id $ROOT_ID \
  --path-part profile \
  --query 'id' \
  --output text 2>/dev/null || \
  aws apigateway get-resources \
    --rest-api-id $API_ID \
    --region $REGION \
    --query 'items[?pathPart==`profile`].id' \
    --output text)
echo "Profile Resource ID: $PROFILE_RESOURCE_ID"

# Get /bookings resource ID
echo "Getting /bookings resource ID..."
BOOKINGS_RESOURCE_ID=$(aws apigateway get-resources \
  --rest-api-id $API_ID \
  --region $REGION \
  --query 'items[?pathPart==`bookings`].id' \
  --output text)
echo "Bookings Resource ID: $BOOKINGS_RESOURCE_ID"

# Create /bookings/history resource
echo "Creating /bookings/history resource..."
HISTORY_RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id $API_ID \
  --region $REGION \
  --parent-id $BOOKINGS_RESOURCE_ID \
  --path-part history \
  --query 'id' \
  --output text 2>/dev/null || \
  aws apigateway get-resources \
    --rest-api-id $API_ID \
    --region $REGION \
    --query 'items[?pathPart==`history`].id' \
    --output text)
echo "History Resource ID: $HISTORY_RESOURCE_ID"

# Setup /profile GET method
echo "Setting up /profile GET method..."
aws apigateway put-method \
  --rest-api-id $API_ID \
  --region $REGION \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method GET \
  --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true \
  2>/dev/null || echo "Method already exists"

PROFILE_LAMBDA_ARN=$(aws lambda get-function \
  --function-name GetUserProfileHandler \
  --region $REGION \
  --query 'Configuration.FunctionArn' \
  --output text)

aws apigateway put-integration \
  --rest-api-id $API_ID \
  --region $REGION \
  --resource-id $PROFILE_RESOURCE_ID \
  --http-method GET \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/$PROFILE_LAMBDA_ARN/invocations \
  2>/dev/null || echo "Integration already exists"

aws lambda add-permission \
  --function-name GetUserProfileHandler \
  --statement-id apigateway-profile-get \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$REGION:*:$API_ID/*/GET/profile" \
  --region $REGION \
  2>/dev/null || echo "Permission already exists"

# Setup /bookings/history GET method
echo "Setting up /bookings/history GET method..."
aws apigateway put-method \
  --rest-api-id $API_ID \
  --region $REGION \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method GET \
  --authorization-type NONE \
  --request-parameters method.request.header.Authorization=true \
  2>/dev/null || echo "Method already exists"

HISTORY_LAMBDA_ARN=$(aws lambda get-function \
  --function-name GetStudentBookingsHandler \
  --region $REGION \
  --query 'Configuration.FunctionArn' \
  --output text)

aws apigateway put-integration \
  --rest-api-id $API_ID \
  --region $REGION \
  --resource-id $HISTORY_RESOURCE_ID \
  --http-method GET \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/$HISTORY_LAMBDA_ARN/invocations \
  2>/dev/null || echo "Integration already exists"

aws lambda add-permission \
  --function-name GetStudentBookingsHandler \
  --statement-id apigateway-history-get \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$REGION:*:$API_ID/*/GET/bookings/history" \
  --region $REGION \
  2>/dev/null || echo "Permission already exists"

# Deploy to dev stage
echo "Deploying to dev stage..."
aws apigateway create-deployment \
  --rest-api-id $API_ID \
  --region $REGION \
  --stage-name dev \
  --description "Added profile and booking history endpoints"

echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo "Profile endpoint: https://$API_ID.execute-api.$REGION.amazonaws.com/dev/profile"
echo "History endpoint: https://$API_ID.execute-api.$REGION.amazonaws.com/dev/bookings/history"
echo ""
echo "Test with:"
echo "curl -X GET https://$API_ID.execute-api.$REGION.amazonaws.com/dev/profile -H 'Authorization: Bearer YOUR_TOKEN'"
