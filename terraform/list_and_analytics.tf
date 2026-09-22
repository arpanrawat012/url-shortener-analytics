# --- list-urls Lambda ---

resource "aws_lambda_function" "list_urls" {
  function_name = "${var.project_name}-list-urls"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.urlshortener.ListUrlsHandler::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 10

  filename         = "${path.module}/../backend/list-urls-function/target/list-urls-function.jar"
  source_code_hash = filebase64sha256("${path.module}/../backend/list-urls-function/target/list-urls-function.jar")

  environment {
    variables = {
      URLS_TABLE_NAME = aws_dynamodb_table.urls.name
    }
  }

  tags = { Project = var.project_name }
}

resource "aws_apigatewayv2_integration" "list_urls" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.list_urls.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "list_urls" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /urls"
  target             = "integrations/${aws_apigatewayv2_integration.list_urls.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_lambda_permission" "allow_apigw_list_urls" {
  statement_id  = "AllowAPIGatewayInvokeListUrls"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.list_urls.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}

# --- analytics Lambda ---

resource "aws_lambda_function" "analytics" {
  function_name = "${var.project_name}-analytics"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.urlshortener.AnalyticsHandler::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 10

  filename         = "${path.module}/../backend/analytics-function/target/analytics-function.jar"
  source_code_hash = filebase64sha256("${path.module}/../backend/analytics-function/target/analytics-function.jar")

  environment {
    variables = {
      URLS_TABLE_NAME         = aws_dynamodb_table.urls.name
      CLICK_EVENTS_TABLE_NAME = aws_dynamodb_table.click_events.name
    }
  }

  tags = { Project = var.project_name }
}

resource "aws_apigatewayv2_integration" "analytics" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.analytics.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "analytics" {
  api_id             = aws_apigatewayv2_api.main.id
  route_key          = "GET /analytics/{shortCode}"
  target             = "integrations/${aws_apigatewayv2_integration.analytics.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_lambda_permission" "allow_apigw_analytics" {
  statement_id  = "AllowAPIGatewayInvokeAnalytics"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.analytics.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}
