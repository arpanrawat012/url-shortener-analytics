resource "aws_lambda_function" "create_url" {
  function_name = "${var.project_name}-create-url"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.urlshortener.CreateUrlHandler::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 10

  filename         = "${path.module}/../backend/create-url-function/target/create-url-function.jar"
  source_code_hash = filebase64sha256("${path.module}/../backend/create-url-function/target/create-url-function.jar")

  environment {
    variables = {
      URLS_TABLE_NAME = aws_dynamodb_table.urls.name
    }
  }

  tags = {
    Project = var.project_name
  }
}
