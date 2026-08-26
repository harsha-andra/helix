terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.116"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # No backend is configured here, and none is committed. State for this
  # project contains the generated Postgres admin password (see postgres.tf
  # / random_password.postgres_admin) and must live in a remote, encrypted
  # backend with restricted access — never on a laptop disk, never in git.
  # Configure the real backend at `terraform init` time with
  # `-backend-config` flags (see README.md) so no environment's storage
  # account name is hardcoded into a file every environment shares.
  backend "azurerm" {}
}
