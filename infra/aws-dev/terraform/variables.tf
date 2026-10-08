variable "aws_region" {
  description = "AWS region near your laptop."
  type        = string
  default     = "us-east-1"
}
variable "name" {
  description = "Name/tag prefix for your development environment."
  type        = string
  default     = "workouts-dev"
}
variable "ssh_cidr" {
  description = "Your laptop's public IPv4 address followed by /32."
  type        = string
  validation {
    condition     = can(cidrnetmask(var.ssh_cidr)) && can(regex("/32$", var.ssh_cidr)) && !contains(["0.0.0.0/32", "203.0.113.10/32"], var.ssh_cidr)
    error_message = "Use a single real public IPv4 address with /32; do not open SSH to the internet."
  }
}
variable "instance_type" {
  description = "x86 T2/T3/T3a instance size; 8 GiB recommended for the full stack."
  type        = string
  default     = "t3.large"
  validation {
    condition     = can(regex("^t(2|3|3a)\\.(medium|large|xlarge|2xlarge)$", var.instance_type))
    error_message = "Choose t2/t3/t3a medium, large, xlarge or 2xlarge; this setup uses an AMD64 AMI."
  }
}
variable "disk_gib" {
  description = "Encrypted gp3 root disk size in GiB; may be increased, not shrunk."
  type        = number
  default     = 60
  validation {
    condition     = var.disk_gib >= 30 && floor(var.disk_gib) == var.disk_gib
    error_message = "Use an integer disk size of at least 30 GiB."
  }
}
variable "cpu_credits" {
  description = "standard caps credit spending; unlimited can incur extra charges."
  type        = string
  default     = "standard"
  validation {
    condition     = contains(["standard", "unlimited"], var.cpu_credits)
    error_message = "Choose standard or unlimited."
  }
}

variable "ssh_public_key_path" {
  description = "Launcher-generated public key path relative to this module; keep the default with setup-vm.sh."
  type        = string
  default     = "../.ssh/id_ed25519.pub"
}
