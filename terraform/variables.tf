variable "aws_region" {
  description = "The AWS region to deploy the microservice"
  type        = string
  default     = "us-east-1"
}

variable "app_name" {
  description = "Name of the application"
  type        = string
  default     = "barcode-scanner-service"
}

variable "app_port" {
  description = "Port exposed by the docker image"
  type        = number
  default     = 8080
}

variable "fargate_cpu" {
  description = "Fargate instance CPU units (512 = 0.5 vCPU)"
  type        = number
  default     = 512
}

variable "fargate_memory" {
  description = "Fargate instance memory (1024 = 1GB)"
  type        = number
  default     = 1024
}
