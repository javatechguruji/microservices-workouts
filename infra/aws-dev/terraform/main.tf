# Terraform creates the AWS resources; setup-vm.sh uploads and initializes the stack.
terraform {
  required_version = ">= 1.6.0, < 2.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

# Credentials come from your AWS CLI profile/environment, never from this file.
provider "aws" {
  region = var.aws_region
  default_tags {
    tags = { Project = var.name, Environment = "development", ManagedBy = "Terraform" }
  }
}

# Canonical's official Ubuntu 24.04 AMD64 image in the selected region.
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# A dedicated network avoids depending on an account's default VPC.
resource "aws_vpc" "dev" {
  cidr_block           = "10.77.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = var.name }
}

# Internet access for SSH, package downloads and Docker registries; no NAT gateway.
resource "aws_internet_gateway" "dev" {
  vpc_id = aws_vpc.dev.id
}
resource "aws_subnet" "dev" {
  vpc_id     = aws_vpc.dev.id
  cidr_block = "10.77.1.0/24"
  tags       = { Name = "${var.name}-public" }
}
resource "aws_route_table" "dev" {
  vpc_id = aws_vpc.dev.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.dev.id
  }
}
resource "aws_route_table_association" "dev" {
  subnet_id      = aws_subnet.dev.id
  route_table_id = aws_route_table.dev.id
}

# Only your laptop's public IPv4 may connect; infrastructure ports stay private.
resource "aws_security_group" "dev" {
  name_prefix = "${var.name}-"
  description = "SSH from developer only"
  vpc_id      = aws_vpc.dev.id
  ingress {
    description = "Developer SSH and tunnels"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Only the PUBLIC key goes to AWS/state; the private key stays on your laptop.
resource "aws_key_pair" "dev" {
  key_name_prefix = "${var.name}-"
  public_key      = file("${path.module}/${var.ssh_public_key_path}")
}

resource "aws_instance" "dev" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.dev.id
  vpc_security_group_ids      = [aws_security_group.dev.id]
  key_name                    = aws_key_pair.dev.key_name
  associate_public_ip_address = true
  tags                        = { Name = var.name }

  # Require session tokens for the EC2 metadata service.
  metadata_options {
    http_tokens = "required"
  }

  # Standard avoids surplus CPU-credit charges, but can throttle after credits run out.
  credit_specification {
    cpu_credits = var.cpu_credits
  }

  # Docker volumes live here; snapshot/back up before intentional destruction.
  root_block_device {
    volume_size           = var.disk_gib
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  lifecycle {
    # Do not rebuild a data-bearing VM just because Canonical published a newer AMI.
    ignore_changes = [ami]
    # Block accidental replacement/destruction; remove only after backing up data.
    prevent_destroy = true
  }
}

# This is a snapshot at apply time; daily.sh queries AWS for the current address.
# AWS releases the automatic public IPv4 on stop and assigns another on start.
output "public_ip" {
  value = aws_instance.dev.public_ip
}
output "instance_id" {
  value = aws_instance.dev.id
}
output "aws_region" {
  value = var.aws_region
}
