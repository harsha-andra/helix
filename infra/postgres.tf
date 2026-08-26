resource "azurerm_postgresql_flexible_server" "main" {
  name                = "psql-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  version             = var.postgres_version

  administrator_login    = var.postgres_admin_username
  administrator_password = random_password.postgres_admin.result

  sku_name   = var.postgres_sku_name
  storage_mb = var.postgres_storage_mb

  # No public endpoint. The only network path in is through the delegated
  # subnet below — matched by helm/values-prod.yaml's
  # networkPolicy.postgres.cidr, which should equal
  # var.postgres_subnet_address_prefix.
  public_network_access_enabled = false
  delegated_subnet_id           = azurerm_subnet.postgres.id
  private_dns_zone_id           = azurerm_private_dns_zone.postgres.id

  zone = "1"

  dynamic "high_availability" {
    for_each = var.postgres_high_availability_enabled ? [1] : []
    content {
      mode = "ZoneRedundant"
    }
  }

  backup_retention_days = var.environment == "prod" ? 14 : 7

  tags = var.tags

  depends_on = [azurerm_private_dns_zone_virtual_network_link.postgres]

  lifecycle {
    # Rotating the admin password should go through Key Vault + a
    # controlled restart, not an incidental `terraform apply` picking up
    # whatever random_password last generated.
    ignore_changes = [administrator_password]
  }
}

resource "azurerm_postgresql_flexible_server_database" "helix" {
  name      = var.postgres_database_name
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}
