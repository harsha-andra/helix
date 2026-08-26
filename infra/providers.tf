provider "azurerm" {
  features {
    key_vault {
      # Soft-delete is not something this project ever wants to bypass on
      # `terraform destroy` — an accidental `terraform destroy` should not
      # also be the reason a production secret becomes unrecoverable.
      purge_soft_delete_on_destroy    = false
      recover_soft_deleted_key_vaults = true
    }
  }

  # No subscription_id / tenant_id is written here or anywhere else in this
  # repository. Locally, `az login` supplies both from your own Azure CLI
  # session; in CI, ARM_* environment variables plus OIDC federation supply
  # them per-run (see .github/workflows/ci.yml and azure-pipelines.yml —
  # both authenticate with `id-token: write` / a federated service
  # connection, not a stored client secret). var.subscription_id and
  # var.tenant_id below default to null specifically so a value never has
  # to be typed into a checked-in file to make this work.
  subscription_id = var.subscription_id
  tenant_id       = var.tenant_id
}

provider "random" {}
