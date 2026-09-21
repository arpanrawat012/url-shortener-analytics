package com.urlshortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RedirectHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final String urlsTableName = System.getenv("URLS_TABLE_NAME");
    private final String clickEventsTableName = System.getenv("CLICK_EVENTS_TABLE_NAME");

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String shortCode = event.getPathParameters().get("shortCode");

            GetItemResponse getResponse = dynamoDb.getItem(GetItemRequest.builder()
                    .tableName(urlsTableName)
                    .key(Map.of("short_code", AttributeValue.builder().s(shortCode).build()))
                    .build());

            if (!getResponse.hasItem()) {
                return APIGatewayV2HTTPResponse.builder()
                        .withStatusCode(404)
                        .withHeaders(Map.of("Content-Type", "text/plain"))
                        .withBody("Short URL not found")
                        .build();
            }

            String longUrl = getResponse.item().get("long_url").s();

            try {
                Map<String, AttributeValue> clickItem = new HashMap<>();
                clickItem.put("short_code", AttributeValue.builder().s(shortCode).build());
                clickItem.put("clicked_at", AttributeValue.builder().s(Instant.now().toString()).build());

                String referrer = event.getHeaders() != null ? event.getHeaders().get("referer") : null;
                if (referrer != null) {
                    clickItem.put("referrer", AttributeValue.builder().s(referrer).build());
                }

                dynamoDb.putItem(PutItemRequest.builder()
                        .tableName(clickEventsTableName)
                        .item(clickItem)
                        .build());
            } catch (Exception loggingError) {
                context.getLogger().log("Failed to log click event: " + loggingError.getMessage());
            }

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(302)
                    .withHeaders(Map.of("Location", longUrl))
                    .withBody("")
                    .build();

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "text/plain"))
                    .withBody("Internal server error")
                    .build();
        }
    }
}