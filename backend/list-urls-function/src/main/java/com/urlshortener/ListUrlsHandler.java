package com.urlshortener;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListUrlsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String tableName = System.getenv("URLS_TABLE_NAME");

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

            QueryResponse queryResponse = dynamoDb.query(QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("owner_id-index")
                    .keyConditionExpression("owner_id = :ownerId")
                    .expressionAttributeValues(Map.of(
                            ":ownerId", AttributeValue.builder().s(userId).build()
                    ))
                    .build());

            List<Map<String, String>> urls = new ArrayList<>();
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                urls.add(Map.of(
                        "shortCode", item.get("short_code").s(),
                        "longUrl", item.get("long_url").s(),
                        "createdAt", item.get("created_at").s()
                ));
            }

            return respond(200, mapper.writeValueAsString(urls));

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
