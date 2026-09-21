package com.urlshortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CreateUrlHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SHORT_CODE_LENGTH = 6;

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String tableName = System.getenv("URLS_TABLE_NAME");

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "POST, OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type, Authorization"
    );

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            if ("OPTIONS".equals(event.getRequestContext().getHttp().getMethod())) {
                return respond(200, "");
            }

            String userId = event.getRequestContext()
                    .getAuthorizer()
                    .getJwt()
                    .getClaims()
                    .get("sub");

            Map<String, String> body = mapper.readValue(event.getBody(), Map.class);
            String longUrl = body.get("longUrl");

            if (longUrl == null || longUrl.isBlank()) {
                return respond(400, "{\"message\": \"longUrl is required\"}");
            }

            String shortCode = generateShortCode();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("short_code", AttributeValue.builder().s(shortCode).build());
            item.put("long_url", AttributeValue.builder().s(longUrl).build());
            item.put("owner_id", AttributeValue.builder().s(userId).build());
            item.put("created_at", AttributeValue.builder().s(Instant.now().toString()).build());

            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            Map<String, String> result = Map.of(
                    "shortCode", shortCode,
                    "longUrl", longUrl
            );

            return respond(200, mapper.writeValueAsString(result));

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return respond(500, "{\"message\": \"Internal server error\"}");
        }
    }

    private String generateShortCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(SHORT_CODE_LENGTH);
        for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    private APIGatewayV2HTTPResponse respond(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(body)
                .build();
    }
}