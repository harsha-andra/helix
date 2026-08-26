# Copy to a real tfvars file (terraform.tfvars, or dev.tfvars/prod.tfvars)
# and edit as needed — .gitignore already excludes every *.tfvars except
# this one, so a real one never gets committed by accident.
#
# subscription_id and tenant_id are deliberately absent: leave them out and
# `az login` (locally) or ARM_SUBSCRIPTION_ID/ARM_TENANT_ID (CI) supply
# them. See providers.tf.

project_name = "helix"
environment  = "dev"
location     = "eastus2"

tags = {
  project = "helix"
  owner   = "platform-team"
}

aks_node_count   = 2
aks_node_vm_size = "Standard_D2s_v5"
aks_sku_tier     = "Free"

postgres_sku_name   = "B_Standard_B1ms"
postgres_storage_mb = 32768

workload_identity_namespace            = "helix"
workload_identity_service_account_name = "helix"
