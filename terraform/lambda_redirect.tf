resource "aws_lambda_function" "redirect" {
  function_name = "${var.project_name}-redirect"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.urlshortener.RedirectHandler::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 10

  filename         = "${path.module}/../backend/redirect-function/target/redirect-function.jar"
  source_code_hash = filebase64sha256("${path.module}/../backend/redirect-function/target/redirect-function.jar")

  environment {
    variables = {
      URLS_TABLE_NAME         = aws_dynamodb_table.urls.name
      CLICK_EVENTS_TABLE_NAME = aws_dynamodb_table.click_events.name
    }
  }

  tags = {
    Project = var.project_name
  }
}