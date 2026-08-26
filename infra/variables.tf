# Every variable below has a sane default so `terraform plan` works with no
# arguments in a scratch subscription; example.tfvars shows the handful an
# environment actually customises. subscription_id and tenant_id default to
# null on purpose — see providers.tf.

variable "subscription_id" {
  description = "Azure subscription id. Leave null to use the ambient az CLI login or ARM_SUBSCRIPTION_ID (CI). Never hardcode this."
  type        = string
  default     = null
}

variable "tenant_id" {
  description = "Azure AD tenant id. Leave null to use the ambient az CLI login or ARM_TENANT_ID (CI). Never hardcode this."
  type        = string
  default     = null
}

variable "project_name" {
  description = "Short name used as a prefix for every resource this stack creates."
  type        = string
  default     = "helix"
}

variable "environment" {
  description = "Deployment environment. Drives resource sizing (see aks.tf, postgres.tf) and is appended to every resource name."
  type        = string
  default     = "dev"
  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be \"dev\" or \"prod\"."
  }
}

variable "location" {
  description = "Azure region for every resource in this stack."
  type        = string
  default     = "eastus2"
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default = {
    project = "helix"
  }
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------

variable "vnet_address_space" {
  type    = list(string)
  default = ["10.20.0.0/16"]
}

variable "aks_subnet_address_prefix" {
  description = "Subnet AKS nodes and pods are provisioned into."
  type        = string
  default     = "10.20.1.0/24"
}

variable "postgres_subnet_address_prefix" {
  description = "Delegated subnet for the Postgres Flexible Server's private endpoint. This is the CIDR the helm chart's networkPolicy.postgres.cidr should match (see helm/values-prod.yaml)."
  type        = string
  default     = "10.20.2.0/24"
}

# ---------------------------------------------------------------------------
# AKS
# ---------------------------------------------------------------------------

variable "aks_kubernetes_version" {
  description = "Kubernetes version. Null lets Azure pick its current default, which is the right choice unless you are pinning for a specific reason."
  type        = string
  default     = null
}

variable "aks_node_count" {
  type    = number
  default = 2
}

variable "aks_node_vm_size" {
  type    = string
  default = "Standard_D2s_v5"
}

variable "aks_sku_tier" {
  description = "Free or Standard. Standard adds the financially-backed SLA and is what production should run; Free is fine for a dev cluster."
  type        = string
  default     = "Free"
}

# ---------------------------------------------------------------------------
# PostgreSQL Flexible Server
# ---------------------------------------------------------------------------

variable "postgres_sku_name" {
  description = "Flexible Server compute/storage tier, e.g. B_Standard_B1ms (dev, burstable) or GP_Standard_D2s_v3 (prod, general purpose)."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  type    = number
  default = 32768
}

variable "postgres_version" {
  type    = string
  default = "16"
}

variable "postgres_admin_username" {
  description = "Server-level admin login. Not the login the application uses at runtime (that is the Key Vault secret the application reads — see keyvault.tf) — this is Postgres's own superuser account, used only by Terraform/an operator, never by the app."
  type        = string
  default     = "helixadmin"
}

variable "postgres_database_name" {
  type    = string
  default = "helix"
}

variable "postgres_high_availability_enabled" {
  description = "Zone-redundant HA standby. Off by default (cost); turn on for a real production SLA."
  type        = bool
  default     = false
}

# ---------------------------------------------------------------------------
# Key Vault
# ---------------------------------------------------------------------------

variable "key_vault_sku_name" {
  type    = string
  default = "standard"
}

# ---------------------------------------------------------------------------
# Service Bus
# ---------------------------------------------------------------------------

variable "servicebus_sku" {
  description = "Standard is the minimum tier that supports topics/subscriptions (Basic does not)."
  type        = string
  default     = "Standard"
}

variable "servicebus_topic_name" {
  description = "Topic the async adjudication path publishes to. Modelled but not yet wired to a live subscription in the app — see docs/ARCHITECTURE.md §8 and ClaimService#markAdjudicated."
  type        = string
  default     = "claim-adjudication"
}

variable "servicebus_subscription_name" {
  type    = string
  default = "helix-app"
}

variable "servicebus_max_delivery_count" {
  description = "Deliveries attempted before a message is moved to the subscription's dead-letter sub-queue."
  type        = number
  default     = 10
}

variable "servicebus_lock_duration" {
  description = "ISO 8601 duration a received message stays locked before it is eligible for redelivery."
  type        = string
  default     = "PT1M"
}

# ---------------------------------------------------------------------------
# Workload identity federation — must match the Helm release's namespace and
# ServiceAccount name exactly, or the federated credential's subject claim
# will not match the token AKS projects into the pod.
# ---------------------------------------------------------------------------

variable "workload_identity_namespace" {
  description = "Kubernetes namespace the Helm release is installed into."
  type        = string
  default     = "helix"
}

variable "workload_identity_service_account_name" {
  description = "Must equal the ServiceAccount name Helm creates — fullnameOverride/nameOverride in helm/values*.yaml if you change it there."
  type        = string
  default     = "helix"
}
