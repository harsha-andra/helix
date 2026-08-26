# Backs the async adjudication path mentioned in docs/ARCHITECTURE.md §8:
# "The async adjudication path is modelled but not wired to Azure Service
# Bus in the local compose stack — markAdjudicated exists with
# REQUIRES_NEW propagation and is called by tests, not by a live
# subscription." This provisions the namespace/topic/subscription that a
# real listener would bind to; wiring the listener itself is application
# code, out of scope for this infra layer.

resource "azurerm_servicebus_namespace" "main" {
  name                = "sb-${local.name_prefix}"
  resource_group_name = azurerm_resource_group.main.name
  location            = azurerm_resource_group.main.location
  sku                 = var.servicebus_sku
  tags                = var.tags
}

resource "azurerm_servicebus_topic" "claim_adjudication" {
  name         = var.servicebus_topic_name
  namespace_id = azurerm_servicebus_namespace.main.id

  default_message_ttl = "P14D"
}

resource "azurerm_servicebus_subscription" "helix_app" {
  name     = var.servicebus_subscription_name
  topic_id = azurerm_servicebus_topic.claim_adjudication.id

  max_delivery_count = var.servicebus_max_delivery_count
  lock_duration      = var.servicebus_lock_duration

  # The dead-letter path: a message that fails processing
  # servicebus_max_delivery_count times, or whose TTL expires while still
  # in the subscription, moves to this subscription's dead-letter
  # sub-queue ($DeadLetterQueue) instead of being silently dropped or
  # retried forever. It stays queryable (Service Bus Explorer, or a
  # dedicated dead-letter consumer) rather than lost.
  dead_lettering_on_message_expiration      = true
  dead_lettering_on_filter_evaluation_error = true
}
