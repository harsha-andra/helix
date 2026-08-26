-- =====================================================================================
-- HELIX baseline schema.
--
-- Hand-written rather than generated. `spring.jpa.hibernate.ddl-auto` is `validate`, never
-- `update`: Hibernate is allowed to check that the mappings agree with this file, and never
-- to change the database. Every schema change after this one ships as a new versioned
-- migration using expand/contract, so a release never requires downtime — see
-- docs/adr/0004-flyway-expand-contract.md.
-- =====================================================================================

-- ---------- reference / party ----------

CREATE TABLE product (
    id                  uuid            PRIMARY KEY,
    code                varchar(24)     NOT NULL UNIQUE,
    name                varchar(120)    NOT NULL,
    line_of_business    varchar(40)     NOT NULL,
    description         varchar(1000),
    active              boolean         NOT NULL DEFAULT true
);

CREATE TABLE claimant (
    id                  uuid            PRIMARY KEY,
    first_name          varchar(80)     NOT NULL,
    last_name           varchar(80)     NOT NULL,
    email               varchar(160)    NOT NULL UNIQUE,
    phone               varchar(32),
    city                varchar(80),
    state               varchar(2)
);
CREATE INDEX idx_claimant_last_name ON claimant (last_name);

CREATE TABLE adjuster (
    id                  uuid            PRIMARY KEY,
    name                varchar(160)    NOT NULL,
    email               varchar(160)    NOT NULL UNIQUE,
    license_number      varchar(32),
    active              boolean         NOT NULL DEFAULT true
);

CREATE TABLE vendor (
    id                  uuid            PRIMARY KEY,
    name                varchar(200)    NOT NULL,
    vendor_type         varchar(32)     NOT NULL,
    tax_id              varchar(32)     UNIQUE,
    email               varchar(160),
    phone               varchar(32),
    city                varchar(80),
    state               varchar(2),
    preferred           boolean         NOT NULL DEFAULT false,
    active              boolean         NOT NULL DEFAULT true
);
CREATE INDEX idx_vendor_type ON vendor (vendor_type);

CREATE TABLE app_user (
    id                  uuid            PRIMARY KEY,
    subject             varchar(160)    NOT NULL UNIQUE,
    display_name        varchar(160)    NOT NULL,
    email               varchar(160)    NOT NULL UNIQUE,
    active              boolean         NOT NULL DEFAULT true,
    created_at          timestamp(6) with time zone NOT NULL,
    last_login_at       timestamp(6) with time zone
);

CREATE TABLE app_user_role (
    user_id             uuid            NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role                varchar(40)     NOT NULL
);
CREATE INDEX idx_user_role_user ON app_user_role (user_id);

-- ---------- policy side ----------

CREATE TABLE policy (
    id                  uuid            PRIMARY KEY,
    policy_number       varchar(32)     NOT NULL UNIQUE,
    product_name        varchar(120)    NOT NULL,
    status              varchar(16)     NOT NULL,
    effective_date      date            NOT NULL,
    expiration_date     date            NOT NULL,
    premium_amount      numeric(15,2)   NOT NULL DEFAULT 0,
    holder_id           uuid            NOT NULL REFERENCES claimant (id),
    product_id          uuid            REFERENCES product (id),
    CONSTRAINT ck_policy_dates CHECK (expiration_date >= effective_date)
);
CREATE INDEX idx_policy_status ON policy (status);
CREATE INDEX idx_policy_holder ON policy (holder_id);

CREATE TABLE coverage (
    id                  uuid            PRIMARY KEY,
    code                varchar(24)     NOT NULL,
    name                varchar(120)    NOT NULL,
    limit_amount        numeric(15,2)   NOT NULL DEFAULT 0,
    deductible_amount   numeric(15,2)   NOT NULL DEFAULT 0,
    policy_id           uuid            NOT NULL REFERENCES policy (id) ON DELETE CASCADE
);
CREATE INDEX idx_coverage_policy ON coverage (policy_id);

CREATE TABLE insured_asset (
    id                  uuid            PRIMARY KEY,
    policy_id           uuid            NOT NULL REFERENCES policy (id) ON DELETE CASCADE,
    asset_type          varchar(24)     NOT NULL,
    identifier          varchar(64),
    description         varchar(300)    NOT NULL,
    year_of_manufacture integer,
    insured_value       numeric(15,2)   NOT NULL DEFAULT 0,
    address_line        varchar(200),
    city                varchar(80),
    state               varchar(2),
    postal_code         varchar(12)
);
CREATE INDEX idx_asset_policy ON insured_asset (policy_id);
CREATE INDEX idx_asset_identifier ON insured_asset (identifier);

CREATE TABLE beneficiary (
    id                  uuid            PRIMARY KEY,
    policy_id           uuid            NOT NULL REFERENCES policy (id) ON DELETE CASCADE,
    full_name           varchar(160)    NOT NULL,
    relationship        varchar(40),
    share_percent       numeric(5,2)    NOT NULL DEFAULT 0,
    email               varchar(160),
    primary_beneficiary boolean         NOT NULL DEFAULT true
);
CREATE INDEX idx_beneficiary_policy ON beneficiary (policy_id);

CREATE TABLE policy_endorsement (
    id                  uuid            PRIMARY KEY,
    policy_id           uuid            NOT NULL REFERENCES policy (id) ON DELETE CASCADE,
    endorsement_number  varchar(32)     NOT NULL,
    endorsement_type    varchar(40)     NOT NULL,
    description         varchar(1000),
    premium_delta       numeric(15,2)   NOT NULL DEFAULT 0,
    effective_date      date            NOT NULL,
    created_at          timestamp(6) with time zone NOT NULL,
    created_by          varchar(160)
);
CREATE INDEX idx_endorsement_policy ON policy_endorsement (policy_id);

-- ---------- claim side ----------

CREATE TABLE claim (
    id                  uuid            PRIMARY KEY,
    claim_number        varchar(32)     NOT NULL UNIQUE,
    policy_id           uuid            NOT NULL REFERENCES policy (id),
    claimant_id         uuid            NOT NULL REFERENCES claimant (id),
    adjuster_id         uuid            REFERENCES adjuster (id),
    status              varchar(24)     NOT NULL,
    incident_date       date            NOT NULL,
    submitted_at        timestamp(6) with time zone NOT NULL,
    closed_at           timestamp(6) with time zone,
    description         varchar(2000),
    loss_type           varchar(40),
    total_amount        numeric(15,2)   NOT NULL DEFAULT 0,
    approved_amount     numeric(15,2),
    version             integer         NOT NULL DEFAULT 0
);
CREATE INDEX idx_claim_status ON claim (status);
CREATE INDEX idx_claim_policy ON claim (policy_id);
CREATE INDEX idx_claim_claimant ON claim (claimant_id);
CREATE INDEX idx_claim_submitted_at ON claim (submitted_at);
-- The claims list is filtered by status and ordered by submission date on nearly every request.
CREATE INDEX idx_claim_status_submitted ON claim (status, submitted_at DESC);

CREATE TABLE claim_line (
    id                  uuid            PRIMARY KEY,
    line_number         integer         NOT NULL,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    coverage_id         uuid            REFERENCES coverage (id),
    coverage_code       varchar(24)     NOT NULL,
    description         varchar(500),
    claimed_amount      numeric(15,2)   NOT NULL DEFAULT 0,
    approved_amount     numeric(15,2),
    status              varchar(16)     NOT NULL
);
CREATE INDEX idx_claim_line_claim ON claim_line (claim_id);

CREATE TABLE claim_document (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    file_name           varchar(255)    NOT NULL,
    content_type        varchar(120)    NOT NULL,
    size_bytes          bigint          NOT NULL,
    document_type       varchar(40),
    storage_key         varchar(512)    NOT NULL,
    uploaded_at         timestamp(6) with time zone NOT NULL,
    uploaded_by         varchar(160)
);
CREATE INDEX idx_document_claim ON claim_document (claim_id);

CREATE TABLE claim_note (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    body                varchar(4000)   NOT NULL,
    author              varchar(160)    NOT NULL,
    internal_only       boolean         NOT NULL DEFAULT true,
    created_at          timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_note_claim ON claim_note (claim_id);

CREATE TABLE claim_status_history (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    from_status         varchar(24),
    to_status           varchar(24)     NOT NULL,
    changed_by          varchar(160)    NOT NULL,
    reason              varchar(500),
    changed_at          timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_status_history_claim ON claim_status_history (claim_id);
CREATE INDEX idx_status_history_changed_at ON claim_status_history (changed_at);

CREATE TABLE claim_assignment (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    adjuster_id         uuid            NOT NULL REFERENCES adjuster (id),
    assigned_at         timestamp(6) with time zone NOT NULL,
    unassigned_at       timestamp(6) with time zone,
    assigned_by         varchar(160)    NOT NULL,
    reason              varchar(300)
);
CREATE INDEX idx_assignment_claim ON claim_assignment (claim_id);
CREATE INDEX idx_assignment_adjuster ON claim_assignment (adjuster_id);

-- ---------- financials ----------

CREATE TABLE reserve (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    reserve_type        varchar(24)     NOT NULL,
    amount              numeric(15,2)   NOT NULL DEFAULT 0,
    previous_amount     numeric(15,2),
    set_by              varchar(160)    NOT NULL,
    rationale           varchar(1000),
    effective_at        timestamp(6) with time zone NOT NULL,
    superseded          boolean         NOT NULL DEFAULT false
);
CREATE INDEX idx_reserve_claim ON reserve (claim_id);

CREATE TABLE payment (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    payee_vendor_id     uuid            REFERENCES vendor (id),
    amount              numeric(15,2)   NOT NULL DEFAULT 0,
    currency            varchar(3)      NOT NULL DEFAULT 'USD',
    status              varchar(16)     NOT NULL,
    method              varchar(24),
    reference           varchar(64)     NOT NULL UNIQUE,
    issued_at           timestamp(6) with time zone,
    settled_at          timestamp(6) with time zone,
    failure_reason      varchar(500)
);
CREATE INDEX idx_payment_claim ON payment (claim_id);
CREATE INDEX idx_payment_status ON payment (status);

CREATE TABLE settlement (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL UNIQUE REFERENCES claim (id) ON DELETE CASCADE,
    gross_amount        numeric(15,2)   NOT NULL DEFAULT 0,
    deductible_applied  numeric(15,2)   NOT NULL DEFAULT 0,
    net_amount          numeric(15,2)   NOT NULL DEFAULT 0,
    status              varchar(24)     NOT NULL,
    agreed_at           timestamp(6) with time zone,
    approved_by         varchar(160),
    release_signed      boolean         NOT NULL DEFAULT false
);

-- ---------- recovery & risk ----------

CREATE TABLE subrogation (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    status              varchar(24)     NOT NULL,
    third_party_name    varchar(200),
    third_party_insurer varchar(200),
    demand_amount       numeric(15,2)   NOT NULL DEFAULT 0,
    recovered_amount    numeric(15,2)   NOT NULL DEFAULT 0,
    opened_at           timestamp(6) with time zone NOT NULL,
    closed_at           timestamp(6) with time zone
);
CREATE INDEX idx_subrogation_claim ON subrogation (claim_id);
CREATE INDEX idx_subrogation_status ON subrogation (status);

CREATE TABLE salvage_recovery (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    vendor_id           uuid            REFERENCES vendor (id),
    asset_description   varchar(300)    NOT NULL,
    estimated_value     numeric(15,2)   NOT NULL DEFAULT 0,
    realised_value      numeric(15,2),
    status              varchar(24)     NOT NULL,
    disposed_at         timestamp(6) with time zone
);
CREATE INDEX idx_salvage_claim ON salvage_recovery (claim_id);

CREATE TABLE fraud_indicator (
    id                  uuid            PRIMARY KEY,
    claim_id            uuid            NOT NULL REFERENCES claim (id) ON DELETE CASCADE,
    rule_code           varchar(40)     NOT NULL,
    description         varchar(500)    NOT NULL,
    severity            varchar(16)     NOT NULL,
    raised_by           varchar(160)    NOT NULL,
    raised_at           timestamp(6) with time zone NOT NULL,
    cleared             boolean         NOT NULL DEFAULT false,
    cleared_reason      varchar(500)
);
CREATE INDEX idx_fraud_claim ON fraud_indicator (claim_id);
CREATE INDEX idx_fraud_severity ON fraud_indicator (severity);

-- ---------- audit ----------

-- Append-only. No UPDATE or DELETE is ever issued against this table by the application;
-- corrections are written as new compensating rows.
CREATE TABLE audit_event (
    id                  uuid            PRIMARY KEY,
    entity_type         varchar(60)     NOT NULL,
    entity_id           uuid            NOT NULL,
    action              varchar(60)     NOT NULL,
    actor               varchar(160)    NOT NULL,
    detail              varchar(2000),
    occurred_at         timestamp(6) with time zone NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_event (entity_type, entity_id);
CREATE INDEX idx_audit_occurred_at ON audit_event (occurred_at DESC);
