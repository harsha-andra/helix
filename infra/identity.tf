# The user-assigned identity the HELIX pod becomes via Workload Identity,
# and the federation trust that lets it. Nothing here is a secret: a
# managed identity has no password/client-secret to leak in the first
# place — Azure AD issues short-lived tokens against the federation trust
# below, not against a stored credential.

resource "azurerm_user_assigned_identity" "helix_app" {
  name                = "id-${local.name_prefix}-app"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  tags                = var.tags
}

# Establishes: "a token requested by the Kubernetes ServiceAccount named
# <workload_identity_service_account_name>, in namespace
# <workload_identity_namespace>, on THIS cluster's OIDC issuer, may be
# exchanged for a token as this Azure identity." Get any one of subject,
# issuer or namespace wrong and workload identity fails closed (the pod
# simply cannot authenticate) rather than open.
resource "azurerm_federated_identity_credential" "helix_app" {
  name                = "fic-${local.name_prefix}-app"
  resource_group_name = azurerm_resource_group.main.name
  parent_id           = azurerm_user_assigned_identity.helix_app.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject             = "system:serviceaccount:${var.workload_identity_namespace}:${var.workload_identity_service_account_name}"
}
