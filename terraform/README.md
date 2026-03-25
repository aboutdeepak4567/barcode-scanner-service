# Production AWS Deployment (Terraform)

This directory contains the Infrastructure-as-Code (IaC) configuration required to deploy the Barcode Scanner Microservice to a highly available, production-grade AWS environment.

## What this provisions:
1. **Network**: A custom VPC with Public Subnets, Internet Gateway, and Route Tables.
2. **Compute**: An **Elastic Container Service (ECS)** cluster utilizing **AWS Fargate** for serverless container execution.
3. **Storage**: An **Elastic Container Registry (ECR)** repository to securely host your Docker images.
4. **Traffic**: An **Application Load Balancer (ALB)** distributing traffic strictly across multiple Availability Zones.
5. **Security & Logging**: Locked down Security Groups, strict IAM Task Execution Roles, and automated CloudWatch logging.

---

## Deployment Instructions

### 1. Initialize & Provision Infrastructure
Ensure you have the [AWS CLI](https://aws.amazon.com/cli/) installed and authenticated (`aws configure`), and [Terraform](https://developer.hashicorp.com/terraform/downloads) installed.

```bash
cd terraform
terraform init
terraform plan
terraform apply
```
*Note the two outputs provided when this finishes: `ecr_repository_url` and `alb_hostname`.*

### 2. Push Docker Image to ECR
Authenticate Docker to your new private ECR repository:
```bash
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <YOUR_ACCOUNT_ID>.dkr.ecr.us-east-1.amazonaws.com
```

Build and push the image from the root of the project:
```bash
cd ..
docker build -t barcode-scanner-service .
docker tag barcode-scanner-service:latest <YOUR_ECR_REPOSITORY_URL>:latest
docker push <YOUR_ECR_REPOSITORY_URL>:latest
```

### 3. Start the ECS Service
Once the image is pushed, ECS Fargate will automatically pull the `latest` tag and boot up your containers behind the Application Load Balancer!

To connect your Vercel frontend, simply update your Vercel `app.js` configuration to point to the `alb_hostname` output provided by Terraform.
