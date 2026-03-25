output "alb_hostname" {
  description = "URL of the Application Load Balancer - Use this in Vercel!"
  value       = "http://${aws_alb.main.dns_name}"
}

output "ecr_repository_url" {
  description = "URL of the Elastic Container Registry - Push your Docker images here"
  value       = aws_ecr_repository.repo.repository_url
}
