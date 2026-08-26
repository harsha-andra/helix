resource "azurerm_kubernetes_cluster" "main" {
  name                = "aks-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  dns_prefix          = "aks-${local.name_prefix}"
  kubernetes_version  = var.aks_kubernetes_version
  sku_tier            = var.aks_sku_tier
  tags                = var.tags

  default_node_pool {
    name           = "system"
    node_count     = var.aks_node_count
    vm_size        = var.aks_node_vm_size
    vnet_subnet_id = azurerm_subnet.aks.id
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin = "azure"
    network_policy = "azure"
  }

  # ---------------------------------------------------------------------
  # These two flags together ARE Azure Workload Identity. oidc_issuer_url
  # (an output below) is the issuer a FederatedIdentityCredential trusts
  # (see identity.tf); workload_identity_enabled turns on the mutating
  # admission webhook that reads the azure.workload.identity/client-id
  # ServiceAccount annotation and azure.workload.identity/use: "true" pod
  # label (both in helm/templates/serviceaccount.yaml and
  # helm/values.yaml) and projects a short-lived, auto-rotated token into
  # the pod. No client secret is stored anywhere in this chain.
  # ---------------------------------------------------------------------
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  lifecycle {
    ignore_changes = [
      # Azure occasionally patches this automatically outside Terraform;
      # do not fight that on every plan.
      default_node_pool[0].node_count,
    ]
  }
}
