# Stores each shortened URL: short_code -> original long URL, owner, created_at
resource "aws_dynamodb_table" "urls" {
  name         = "${var.project_name}-urls"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"

  attribute {
    name = "short_code"
    type = "S"
  }

  attribute {
    name = "owner_id"
    type = "S"
  }

  # Lets us query "all URLs belonging to this user" without scanning the whole table
  global_secondary_index {
    name            = "owner_id-index"
    hash_key        = "owner_id"
    projection_type = "ALL"
  }

  tags = {
    Project = var.project_name
  }
}

# Stores every click event for analytics: short_code + timestamp as the composite key
resource "aws_dynamodb_table" "click_events" {
  name         = "${var.project_name}-click-events"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "short_code"
  range_key    = "clicked_at"

  attribute {
    name = "short_code"
    type = "S"
  }

  attribute {
    name = "clicked_at"
    type = "S"
  }

  tags = {
    Project = var.project_name
  }
}
