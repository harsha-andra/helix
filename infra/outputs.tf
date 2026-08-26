# Feed these into helm/values-dev.yaml or values-prod.yaml (or `--set`) —
# none of them are secret.

output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "aks_cluster_name" {
  value = azurerm_kubernetes_cluster.main.name
}

output "aks_oidc_issuer_url" {
  description = "The trust anchor for every workload identity federation in this cluster."
  value       = azurerm_kubernetes_cluster.main.oidc_issuer_url
}

output "aks_get_credentials_command" {
  value = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${azurerm_kubernetes_cluster.main.name} --overwrite-existing"
}

output "workload_identity_client_id" {
  description = "-> helm values: serviceAccount.workloadIdentityClientId"
  value       = azurerm_user_assigned_identity.helix_app.client_id
}

output "key_vault_name" {
  description = "-> helm values: secretProviderClass.keyvaultName"
  value       = azurerm_key_vault.main.name
}

output "key_vault_tenant_id" {
  description = "-> helm values: secretProviderClass.tenantId"
  value       = data.azurerm_client_config.current.tenant_id
}

output "postgres_fqdn" {
  description = "-> helm values: database.host"
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "postgres_database_name" {
  value = azurerm_postgresql_flexible_server_database.helix.name
}

output "servicebus_namespace_name" {
  value = azurerm_servicebus_namespace.main.name
}

output "servicebus_topic_name" {
  value = azurerm_servicebus_topic.claim_adjudication.name
}

output "servicebus_subscription_name" {
  value = azurerm_servicebus_subscription.helix_app.name
}
