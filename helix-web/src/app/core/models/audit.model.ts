export interface AuditEvent {
  id: string;
  entityType: string;
  entityId: string;
  action: string;
  actor: string;
  occurredAt: string;
  detail: string;
}
