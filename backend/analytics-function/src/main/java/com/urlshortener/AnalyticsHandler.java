package com.urlshortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnalyticsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String urlsTableName = System.getenv("URLS_TABLE_NAME");
    private final String clickEventsTableName = System.getenv("CLICK_EVENTS_TABLE_NAME");

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET, OPTIONS",
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

            String shortCode = event.getPathParameters().get("shortCode");

            // Ownership check - make sure this URL actually belongs to the requesting user
            GetItemResponse urlItem = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(urlsTableName)
                    .key(Map.of("short_code", AttributeValue.builder().s(shortCode).build()))
                    .build());

            if (!urlItem.hasItem()) {
                return respond(404, "{\"message\": \"Short URL not found\"}");
            }

            String ownerId = urlItem.item().get("owner_id").s();
            if (!ownerId.equals(userId)) {
                return respond(403, "{\"message\": \"You do not own this URL\"}");
            }

            // Fetch all click events for this short code
            QueryResponse clicksResponse = dynamoDb.query(QueryRequest.builder()
                    .tableName(clickEventsTableName)
                    .keyConditionExpression("short_code = :code")
                    .expressionAttributeValues(Map.of(
                            ":code", AttributeValue.builder().s(shortCode).build()
                    ))
                    .build());

            List<Map<String, String>> clicks = new ArrayList<>();
            for (Map<String, AttributeValue> item : clicksResponse.items()) {
                clicks.add(Map.of(
                        "clickedAt", item.get("clicked_at").s(),
                        "referrer", item.containsKey("referrer") ? item.get("referrer").s() : "direct"
                ));
            }

            Map<String, Object> result = Map.of(
                    "shortCode", shortCode,
                    "totalClicks", clicks.size(),
                    "clicks", clicks
            );

            return respond(200, mapper.writeValueAsString(result));

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return respond(500, "{\"message\": \"Internal server error\"}");
        }
    }

    private APIGatewayV2HTTPResponse respond(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(CORS_HEADERS)
                .withBody(body)
                .build();
    }
}
