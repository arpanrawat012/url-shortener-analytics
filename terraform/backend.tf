terraform {
  backend "s3" {
    bucket = "arpan-url-shortner-tfstate"
    key    = "url-shortener-analytics/terraform.tfstate"
    region = "ap-south-1"
  }
}