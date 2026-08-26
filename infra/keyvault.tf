data "azurerm_client_config" "current" {}

resource "azurerm_key_vault" "main" {
  name                       = substr("kv-${replace(local.name_prefix, "-", "")}", 0, 24)
  resource_group_name        = azurerm_resource_group.main.name
  location                   = azurerm_resource_group.main.location
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = var.key_vault_sku_name
  soft_delete_retention_days = 7
  purge_protection_enabled   = var.environment == "prod"
  tags                       = var.tags
}

# The one access policy in this whole stack that matters at runtime: it is
# what lets the pod's federated identity actually read the two secrets
# below. Get vs. List vs. Set is deliberately minimal — the running
# application only ever needs Get (and the Secrets Store CSI Driver needs
# List to resolve the objects named in the SecretProviderClass); nothing
# here can create, delete or rotate a secret.
resource "azurerm_key_vault_access_policy" "helix_app" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = azurerm_user_assigned_identity.helix_app.principal_id

  secret_permissions = ["Get", "List"]
}

# Terraform's own identity needs Set/Delete to manage the two secrets
# below; this is the operator/CI principal running `terraform apply`, not
# the application's identity.
resource "azurerm_key_vault_access_policy" "terraform_operator" {
  key_vault_id = azurerm_key_vault.main.id
  tenant_id    = data.azurerm_client_config.current.tenant_id
  object_id    = data.azurerm_client_config.current.object_id

  secret_permissions = ["Get", "List", "Set", "Delete", "Purge"]
}

# Generated, not chosen — nobody types this in, nobody needs to know it,
# and it never appears in a values file or a manifest. It lands only in
# two places: this Terraform state (see the note in versions.tf about
# keeping state in a secure remote backend) and the Key Vault secret below.
resource "random_password" "postgres_admin" {
  length      = 32
  special     = true
  min_upper   = 2
  min_lower   = 2
  min_numeric = 2
  min_special = 2
}


# NOTE ON SCOPE: these two secrets are the Postgres Flexible Server's own
# administrator login (see postgres.tf), handed straight to the app. A
# hardened setup would instead provision a least-privileged, database-only
# role (e.g. via the `cyrilgdn/postgresql` Terraform provider, connecting
# over the same private endpoint) and put THAT role's credentials here
# instead, keeping the server admin account for break-glass/operator use
# only. Left as the simpler shape for this stack's scope — worth doing
# before this touches real customer data.
resource "azurerm_key_vault_secret" "db_username" {
  name         = "helix-db-username"
  value        = var.postgres_admin_username
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_key_vault_access_policy.terraform_operator]
}

resource "azurerm_key_vault_secret" "db_password" {
  name         = "helix-db-password"
  value        = random_password.postgres_admin.result
  key_vault_id = azurerm_key_vault.main.id
  depends_on   = [azurerm_key_vault_access_policy.terraform_operator]
}
