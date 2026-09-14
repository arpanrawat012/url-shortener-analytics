output "urls_table_name" {
  value = aws_dynamodb_table.urls.name
}

output "click_events_table_name" {
  value = aws_dynamodb_table.click_events.name
}

output "create_url_function_name" {
  value = aws_lambda_function.create_url.function_name
}

output "api_base_url" {
  value = aws_apigatewayv2_api.main.api_endpoint
}