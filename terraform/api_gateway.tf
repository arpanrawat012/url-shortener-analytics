resource "aws_apigatewayv2_api" "main" {
  name          = "${var.project_name}-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_origins = ["*"]
    allow_methods = ["GET", "POST", "OPTIONS"]
    allow_headers = ["Content-Type", "Authorization"]
  }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "create_url" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.create_url.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "create_url" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /urls"
  target    = "integrations/${aws_apigatewayv2_integration.create_url.id}"
}

resource "aws_lambda_permission" "allow_apigw_create_url" {
  statement_id  = "AllowAPIGatewayInvokeCreateUrl"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.create_url.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*"
}