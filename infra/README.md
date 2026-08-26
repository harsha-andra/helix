# HELIX infrastructure (Terraform)

Provisions the Azure resources HELIX's Helm chart deploys onto: an AKS
cluster with OIDC issuer + Workload Identity enabled, a Key Vault holding
the database credentials, a Postgres Flexible Server reachable only over a
private endpoint, and a Service Bus namespace/topic/subscription (with a
dead-letter path) for the async adjudication work described in
`docs/ARCHITECTURE.md` §8.

## Layout

| File | Provisions |
|---|---|
| `versions.tf` | Provider version pins, backend declaration (empty — see below) |
| `providers.tf` | `azurerm`/`random` provider config — no subscription/tenant id in this repo |
| `variables.tf` | Every input, with defaults |
| `network.tf` | Resource group, VNet, AKS subnet, delegated Postgres subnet, private DNS zone |
| `aks.tf` | The AKS cluster — `oidc_issuer_enabled` + `workload_identity_enabled`, Azure CNI with `network_policy = "azure"` (without this, Kubernetes `NetworkPolicy` objects like `helm/templates/networkpolicy.yaml` are silently unenforced) |
| `identity.tf` | User-assigned identity + federated credential trusting the AKS OIDC issuer |
| `keyvault.tf` | Key Vault, access policies, the generated DB credential stored as two secrets |
| `postgres.tf` | Postgres Flexible Server, private-endpoint-only, plus the `helix` database |
| `servicebus.tf` | Namespace, topic, subscription with dead-lettering |
| `outputs.tf` | Values to feed into `helm/values-{dev,prod}.yaml` |
| `example.tfvars` | A starting point — copy it, do not commit the copy |

## No state is committed

`.gitignore` at the repo root excludes `.terraform/`, `*.tfstate`, and
every `*.tfvars` except `example.tfvars`. This state file contains a
generated database password (`random_password.postgres_admin` in
`keyvault.tf`) — it must live in a remote backend with its own access
control (an `azurerm` storage account + container, encrypted at rest,
with RBAC restricting who can read it), never on a laptop and never in
version control.

Configure the backend at init time rather than hardcoding a storage
account name that every environment would otherwise share:

```bash
terraform init \
  -backend-config="resource_group_name=rg-helix-tfstate" \
  -backend-config="storage_account_name=<your-tfstate-storage-account>" \
  -backend-config="container_name=tfstate" \
  -backend-config="key=helix-<environment>.tfstate"
```

## Authentication — never a hardcoded subscription or tenant id

Locally:

```bash
az login
az account set --subscription "<name-or-id>"   # picks the subscription for this shell session only
terraform init ...
terraform plan -var-file=my.tfvars
```

`var.subscription_id` and `var.tenant_id` (`variables.tf`) both default to
`null`; the `azurerm` provider then falls back to whatever `az login`
already established. In CI (`.github/workflows/*.yml`,
`azure-pipelines.yml`), the same two values arrive via `ARM_SUBSCRIPTION_ID`
/ `ARM_TENANT_ID` environment variables set from an OIDC-federated login —
see the comments in those pipeline files — so no client secret and no
subscription id ever needs to be typed into a file in this repository.

## Usage

```bash
cp example.tfvars dev.tfvars     # edit environment, sizing, tags
terraform init -backend-config=... # see above
terraform plan  -var-file=dev.tfvars
terraform apply -var-file=dev.tfvars
```

After apply, wire the outputs into Helm:

```bash
terraform output -json > /tmp/helix-infra-outputs.json
# workload_identity_client_id -> serviceAccount.workloadIdentityClientId
# key_vault_name              -> secretProviderClass.keyvaultName
# key_vault_tenant_id         -> secretProviderClass.tenantId
# postgres_fqdn               -> database.host
```

`terraform destroy -var-file=dev.tfvars` tears everything in this stack
down; Key Vault's soft-delete means the vault name is reserved for its
retention period afterward (`soft_delete_retention_days` in `keyvault.tf`).

## What this does not do

- It does not create the Postgres role the application actually connects
  as — see the note in `keyvault.tf`. The server admin credential is
  handed to the app directly, which is adequate for this project's scope
  and not what a production rollout should ship with.
- It does not install the Secrets Store CSI Driver, the Prometheus
  Operator CRDs, or an ingress controller onto the cluster — those are
  cluster add-ons, installed once per cluster (e.g. via the AKS
  `azureKeyvaultSecretsProvider` add-on and `kube-prometheus-stack`), not
  per-application infrastructure this stack owns.
- `terraform validate`/`plan` could not be executed against a real Azure
  subscription while building this — see the root `README.md` for what was
  and was not verified in this environment.
