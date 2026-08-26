import { Adjuster, Claimant, ClaimDetail, ClaimLine, ClaimStatus, Coverage, DocumentRef, PolicyDetail } from '../models';

/** Deterministic PRNG (mulberry32) so the demo dataset is identical on every reload within a session. */
function mulberry32(seed: number) {
  let a = seed;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const rng = mulberry32(20250826);

function pick<T>(arr: T[]): T {
  return arr[Math.floor(rng() * arr.length)];
}

function pickN<T>(arr: T[], n: number): T[] {
  const copy = [...arr];
  const out: T[] = [];
  for (let i = 0; i < n && copy.length > 0; i++) {
    const idx = Math.floor(rng() * copy.length);
    out.push(copy.splice(idx, 1)[0]);
  }
  return out;
}

function randInt(min: number, max: number): number {
  return Math.floor(rng() * (max - min + 1)) + min;
}

function randMoney(min: number, max: number, step = 50): number {
  const n = randInt(Math.round(min / step), Math.round(max / step)) * step;
  return n;
}

function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString();
}

function daysFromNow(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return d.toISOString();
}

function uuid(prefix: string, i: number): string {
  return `${prefix}-${String(i).padStart(6, '0')}`;
}

const FIRST_NAMES = [
  'James', 'Mary', 'Robert', 'Patricia', 'John', 'Jennifer', 'Michael', 'Linda', 'David', 'Elizabeth',
  'William', 'Barbara', 'Richard', 'Susan', 'Joseph', 'Jessica', 'Thomas', 'Sarah', 'Christopher', 'Karen',
  'Charles', 'Nancy', 'Daniel', 'Lisa', 'Matthew', 'Margaret', 'Anthony', 'Betty', 'Mark', 'Sandra',
  'Donald', 'Ashley', 'Steven', 'Dorothy', 'Andrew', 'Kimberly', 'Paul', 'Emily', 'Joshua', 'Donna',
  'Kenneth', 'Michelle', 'Kevin', 'Carol', 'Brian', 'Amanda', 'George', 'Melissa', 'Edward', 'Deborah',
  'Ronald', 'Stephanie', 'Timothy', 'Rebecca', 'Jason', 'Laura', 'Jeffrey', 'Sharon', 'Ryan', 'Cynthia',
  'Jacob', 'Kathleen', 'Gary', 'Amy', 'Nicholas', 'Angela', 'Eric', 'Shirley', 'Jonathan', 'Anna',
  'Carlos', 'Maria', 'Luis', 'Sofia', 'Wei', 'Priya', 'Aisha', 'Mohammed', 'Yuki', 'Diego',
];
const LAST_NAMES = [
  'Smith', 'Johnson', 'Williams', 'Brown', 'Jones', 'Garcia', 'Miller', 'Davis', 'Rodriguez', 'Martinez',
  'Hernandez', 'Lopez', 'Gonzalez', 'Wilson', 'Anderson', 'Thomas', 'Taylor', 'Moore', 'Jackson', 'Martin',
  'Lee', 'Perez', 'Thompson', 'White', 'Harris', 'Sanchez', 'Clark', 'Ramirez', 'Lewis', 'Robinson',
  'Walker', 'Young', 'Allen', 'King', 'Wright', 'Scott', 'Torres', 'Nguyen', 'Hill', 'Flores',
  'Green', 'Adams', 'Nelson', 'Baker', 'Hall', 'Rivera', 'Campbell', 'Mitchell', 'Carter', 'Roberts',
];
const CITY_STATE: [string, string][] = [
  ['Columbus', 'OH'], ['Austin', 'TX'], ['Denver', 'CO'], ['Charlotte', 'NC'], ['Phoenix', 'AZ'],
  ['Portland', 'OR'], ['Nashville', 'TN'], ['Indianapolis', 'IN'], ['Sacramento', 'CA'], ['Kansas City', 'MO'],
  ['Raleigh', 'NC'], ['Omaha', 'NE'], ['Tampa', 'FL'], ['Tucson', 'AZ'], ['Albuquerque', 'NM'],
  ['Fresno', 'CA'], ['Mesa', 'AZ'], ['Atlanta', 'GA'], ['Colorado Springs', 'CO'], ['Louisville', 'KY'],
  ['Milwaukee', 'WI'], ['Baltimore', 'MD'], ['Boise', 'ID'], ['Richmond', 'VA'], ['Spokane', 'WA'],
  ['Des Moines', 'IA'], ['Springfield', 'MO'], ['Providence', 'RI'], ['Madison', 'WI'], ['Columbia', 'SC'],
];
const STREETS = ['Maple Ave', 'Oak St', 'Cedar Ln', 'Birch Rd', 'Elm St', 'Willow Way', 'Sunset Blvd', 'Highland Dr', 'Riverside Dr', 'Prairie Ct'];
const PRODUCT_NAMES = [
  'Homeowners Advantage', 'Auto Shield Plus', 'Commercial Property Guard', 'Umbrella Protector',
  'Renters Essential', 'Auto Standard', 'Homeowners Elite', 'Condo Guard', 'Landlord Protector', 'Motorcycle Shield',
];
const COVERAGE_TYPES: { code: string; name: string }[] = [
  { code: 'BI', name: 'Bodily Injury Liability' },
  { code: 'PD', name: 'Property Damage Liability' },
  { code: 'COMP', name: 'Comprehensive' },
  { code: 'COLL', name: 'Collision' },
  { code: 'MED', name: 'Medical Payments' },
  { code: 'UM', name: 'Uninsured Motorist' },
  { code: 'DWELL', name: 'Dwelling Coverage' },
  { code: 'PERS', name: 'Personal Property' },
  { code: 'LOU', name: 'Loss of Use' },
  { code: 'LIAB', name: 'Personal Liability' },
];
const INCIDENT_DESCRIPTIONS = [
  'Rear-end collision at intersection during evening commute.',
  'Kitchen fire caused smoke damage to cabinetry and ceiling.',
  'Wind storm damaged roof shingles and gutters.',
  'Water heater failure caused basement flooding.',
  'Vehicle struck a guardrail on icy roadway.',
  'Tree fell on detached garage during storm.',
  'Theft of personal property from insured residence.',
  'Hail damage to vehicle exterior and windshield.',
  'Slip and fall on insured commercial property.',
  'Pipe burst under kitchen sink causing water damage.',
  'Multi-vehicle collision on interstate highway.',
  'Lightning strike damaged home electrical system.',
  'Vandalism to insured vehicle in parking structure.',
  'Windstorm caused fence and shed damage.',
  'Backup of sewer line caused property damage.',
];
const DOC_NAMES = [
  'incident-report.pdf', 'police-report.pdf', 'photos-damage-1.jpg', 'photos-damage-2.jpg',
  'repair-estimate.pdf', 'medical-records.pdf', 'proof-of-loss.pdf', 'witness-statement.pdf',
  'contractor-invoice.pdf', 'vehicle-title.pdf',
];
const DOC_TYPES: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  png: 'image/png',
};

export const ADJUSTERS: Adjuster[] = [
  'Melissa Ortega', 'Derek Chandler', 'Priya Nair', 'Samuel Whitfield', 'Angela Ferraro',
  'Tom Bradshaw', 'Nina Kowalski', 'Marcus Webb',
].map((name, i) => ({
  id: uuid('ADJ', i + 1),
  name,
  email: `${name.toLowerCase().replace(' ', '.')}@helixinsurance.com`,
  activeClaims: 0,
}));

export const CLAIMANTS: Claimant[] = Array.from({ length: 40 }).map((_, i) => {
  const first = pick(FIRST_NAMES);
  const last = pick(LAST_NAMES);
  const [city, state] = pick(CITY_STATE);
  return {
    id: uuid('CLT', i + 1),
    firstName: first,
    lastName: last,
    email: `${first.toLowerCase()}.${last.toLowerCase()}@${pick(['gmail.com', 'yahoo.com', 'outlook.com', 'icloud.com'])}`,
    phone: `(${randInt(200, 989)}) ${randInt(200, 999)}-${String(randInt(0, 9999)).padStart(4, '0')}`,
    city,
    state,
  } as Claimant;
});

function buildCoverages(n: number): Coverage[] {
  const types = pickN(COVERAGE_TYPES, n);
  return types.map((t, i) => ({
    id: uuid('COV', i + 1 + randInt(1, 999999)),
    code: t.code,
    name: t.name,
    limitAmount: randMoney(25000, 500000, 5000),
    deductibleAmount: pick([250, 500, 1000, 2500]),
  }));
}

export const POLICIES: PolicyDetail[] = Array.from({ length: 25 }).map((_, i) => {
  const holder = `${pick(FIRST_NAMES)} ${pick(LAST_NAMES)}`;
  const coverages = buildCoverages(randInt(2, 5));
  const effectiveDate = daysAgo(randInt(30, 700));
  const status = randInt(1, 100) <= 85 ? 'ACTIVE' : pick(['LAPSED', 'CANCELLED', 'EXPIRED']);
  return {
    id: uuid('POL', i + 1),
    policyNumber: `POL-2024-${String(18000 + i * 37 + randInt(1, 36)).padStart(6, '0')}`,
    productName: pick(PRODUCT_NAMES),
    holderName: holder,
    status: status as PolicyDetail['status'],
    effectiveDate,
    expirationDate: new Date(new Date(effectiveDate).setFullYear(new Date(effectiveDate).getFullYear() + 1)).toISOString(),
    premiumAmount: randMoney(600, 4200, 25),
    coverageCount: coverages.length,
    coverages,
  };
});
// Ensure the API-contract example policy number exists verbatim in the seed set.
POLICIES[0].policyNumber = 'POL-2024-018842';

const STATUS_WEIGHTS: [ClaimStatus, number][] = [
  ['SUBMITTED', 12],
  ['UNDER_REVIEW', 16],
  ['APPROVED', 14],
  ['PARTIALLY_APPROVED', 8],
  ['DENIED', 8],
  ['PAID', 22],
  ['CLOSED', 20],
];

function weightedStatus(): ClaimStatus {
  const total = STATUS_WEIGHTS.reduce((s, [, w]) => s + w, 0);
  let r = rng() * total;
  for (const [status, w] of STATUS_WEIGHTS) {
    if (r < w) return status;
    r -= w;
  }
  return 'SUBMITTED';
}

function buildLines(coverages: Coverage[], status: ClaimStatus): ClaimLine[] {
  const n = randInt(1, Math.min(4, coverages.length) || 1);
  const chosen = pickN(coverages.length ? coverages : COVERAGE_TYPES.map((c, i) => ({ id: uuid('COV', i), code: c.code, name: c.name, limitAmount: 0, deductibleAmount: 0 })), n);
  return chosen.map((cov, i) => {
    const claimed = randMoney(500, 28000, 50);
    const decided = ['APPROVED', 'PARTIALLY_APPROVED', 'DENIED', 'PAID', 'CLOSED'].includes(status);
    let lineStatus: ClaimLine['status'] = 'PENDING';
    let approved: number | null = null;
    if (decided) {
      if (status === 'DENIED') {
        lineStatus = 'DENIED';
        approved = 0;
      } else {
        lineStatus = 'APPROVED';
        approved = status === 'PARTIALLY_APPROVED' ? Math.round(claimed * (0.4 + rng() * 0.4)) : claimed;
      }
    }
    return {
      id: uuid('LN', i + 1 + randInt(1, 999999)),
      lineNumber: i + 1,
      coverageCode: cov.code,
      description: `${cov.name} — ${pick(['repair', 'replacement', 'medical expense', 'rental reimbursement', 'assessment'])}`,
      claimedAmount: claimed,
      approvedAmount: approved,
      status: lineStatus,
    };
  });
}

function buildDocuments(n: number, baseDate: string): DocumentRef[] {
  const names = pickN(DOC_NAMES, n);
  return names.map((name, i) => {
    const ext = name.split('.').pop()!;
    return {
      id: uuid('DOC', i + 1 + randInt(1, 999999)),
      fileName: name,
      contentType: DOC_TYPES[ext] ?? 'application/octet-stream',
      sizeBytes: randInt(40_000, 4_800_000),
      uploadedAt: baseDate,
    };
  });
}

function auditTrailFor(claim: { id: string; status: ClaimStatus; submittedAt: string; assignedAdjuster: string | null }): { id: string; entityType: string; entityId: string; action: string; actor: string; occurredAt: string; detail: string }[] {
  const events: { id: string; entityType: string; entityId: string; action: string; actor: string; occurredAt: string; detail: string }[] = [];
  let t = new Date(claim.submittedAt).getTime();
  const push = (action: string, actor: string, detail: string) => {
    t += randInt(2, 36) * 3600 * 1000;
    events.push({
      id: uuid('EVT', events.length + 1 + randInt(1, 999999)),
      entityType: 'CLAIM',
      entityId: claim.id,
      action,
      actor,
      occurredAt: new Date(t).toISOString(),
      detail,
    });
  };
  push('CLAIM_SUBMITTED', claim.assignedAdjuster ?? 'System', 'Claim submitted via online portal.');
  if (claim.status !== 'SUBMITTED') {
    push('STATUS_CHANGED', claim.assignedAdjuster ?? 'System', 'Status changed from SUBMITTED to UNDER_REVIEW.');
  }
  if (!['SUBMITTED', 'UNDER_REVIEW'].includes(claim.status)) {
    push('STATUS_CHANGED', claim.assignedAdjuster ?? 'System', `Status changed from UNDER_REVIEW to ${claim.status}.`);
  }
  if (['PAID', 'CLOSED'].includes(claim.status)) {
    push('PAYMENT_ISSUED', claim.assignedAdjuster ?? 'System', 'Payment issued to claimant.');
  }
  if (claim.status === 'CLOSED') {
    push('CLAIM_CLOSED', claim.assignedAdjuster ?? 'System', 'Claim closed — no further action required.');
  }
  return events.reverse();
}

export const CLAIMS: ClaimDetail[] = Array.from({ length: 60 }).map((_, i) => {
  const policy = pick(POLICIES);
  const claimant = pick(CLAIMANTS);
  const status = weightedStatus();
  const submittedDaysAgo = randInt(2, 260);
  const submittedAt = daysAgo(submittedDaysAgo);
  const incidentDate = daysAgo(submittedDaysAgo + randInt(1, 10));
  const adjuster = status === 'SUBMITTED' && rng() < 0.3 ? null : pick(ADJUSTERS);
  const lines = buildLines(policy.coverages, status);
  const totalAmount = lines.reduce((s, l) => s + l.claimedAmount, 0);
  const documents = buildDocuments(randInt(1, 4), daysAgo(submittedDaysAgo - 1 < 0 ? 0 : submittedDaysAgo - 1));

  const claim: ClaimDetail = {
    id: uuid('CLM', i + 1),
    claimNumber: `CLM-2025-${String(4380 + i * 6 + randInt(1, 5)).padStart(6, '0')}`,
    policyNumber: policy.policyNumber,
    claimantName: `${claimant.firstName} ${claimant.lastName}`,
    status,
    totalAmount,
    incidentDate,
    submittedAt,
    assignedAdjuster: adjuster?.name ?? null,
    lineCount: lines.length,
    version: randInt(1, 4),
    policy,
    claimant,
    adjuster,
    description: pick(INCIDENT_DESCRIPTIONS),
    lines,
    documents,
  };
  return claim;
});

// The API-contract sample claim number is planted verbatim, and doubles as the deterministic
// "always fails" claim used to demo the optimistic-update rollback (see demo-api.interceptor.ts).
// Its status is pinned to UNDER_REVIEW (rather than whatever the random seed produced) so it
// always has a real "next status" transition available to attempt in the UI.
CLAIMS[16].claimNumber = 'CLM-2025-004417';
CLAIMS[16].status = 'UNDER_REVIEW';
CLAIMS[16].adjuster = CLAIMS[16].adjuster ?? ADJUSTERS[0];
CLAIMS[16].assignedAdjuster = CLAIMS[16].adjuster.name;
CLAIMS[16].lines = CLAIMS[16].lines.map((line) => ({ ...line, approvedAmount: null, status: 'PENDING' as const }));
export const ALWAYS_FAILS_STATUS_UPDATE_CLAIM_ID = CLAIMS[16].id;

CLAIMS.forEach((c) => {
  if (c.adjuster) {
    const a = ADJUSTERS.find((x) => x.id === c.adjuster!.id);
    if (a && !['PAID', 'CLOSED', 'DENIED'].includes(c.status)) a.activeClaims += 1;
  }
});

export const AUDIT_BY_CLAIM: Record<string, ReturnType<typeof auditTrailFor>> = {};
CLAIMS.forEach((c) => {
  AUDIT_BY_CLAIM[c.id] = auditTrailFor(c);
});
