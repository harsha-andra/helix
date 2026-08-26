package com.harshaandra.helix.config;

import com.harshaandra.helix.domain.model.*;
import com.harshaandra.helix.domain.repository.ClaimRepository;
import com.harshaandra.helix.domain.repository.ClaimantRepository;
import com.harshaandra.helix.domain.repository.PolicyRepository;
import com.harshaandra.helix.domain.repository.AdjusterRepository;
import com.harshaandra.helix.domain.repository.AuditEventRepository;
import com.harshaandra.helix.domain.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeds a realistic book of business so that a clean clone is immediately explorable — and so
 * the N+1 demonstration has enough rows to be meaningful.
 *
 * Deterministic: the Random is seeded with a constant, so the same clone produces the same data
 * and a screenshot in the docs still matches what a reviewer sees.
 */
@Configuration
@ConditionalOnProperty(name = "helix.seed.enabled", havingValue = "true")
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private static final String[] FIRST_NAMES = {
            "James", "Maria", "Robert", "Linda", "Michael", "Patricia", "David", "Jennifer",
            "William", "Elizabeth", "Richard", "Barbara", "Joseph", "Susan", "Thomas", "Jessica",
            "Charles", "Sarah", "Daniel", "Karen", "Aisha", "Wei", "Priya", "Diego", "Fatima"
    };
    private static final String[] LAST_NAMES = {
            "Nguyen", "Okafor", "Martinez", "Chen", "Patel", "Johnson", "Rodriguez", "Kim",
            "Silva", "Anderson", "Hassan", "Kowalski", "Thompson", "Rivera", "Murphy",
            "Sullivan", "Vargas", "Novak", "Brennan", "Castillo"
    };
    private static final String[][] CITIES = {
            {"Newark", "NJ"}, {"Jersey City", "NJ"}, {"New York", "NY"}, {"Brooklyn", "NY"},
            {"Philadelphia", "PA"}, {"Stamford", "CT"}, {"Hartford", "CT"}, {"Boston", "MA"},
            {"Providence", "RI"}, {"Wilmington", "DE"}
    };
    private static final String[] LOSS_TYPES = {
            "COLLISION", "WATER_DAMAGE", "FIRE", "THEFT", "WIND_HAIL", "LIABILITY", "GLASS"
    };

    @Bean
    ApplicationRunner seedRunner(ProductRepository products,
                                 ClaimantRepository claimants,
                                 AdjusterRepository adjusters,
                                 PolicyRepository policies,
                                 ClaimRepository claims,
                                 AuditEventRepository audits) {
        return args -> seed(products, claimants, adjusters, policies, claims, audits);
    }

    @Transactional
    void seed(ProductRepository productRepo,
              ClaimantRepository claimantRepo,
              AdjusterRepository adjusterRepo,
              PolicyRepository policyRepo,
              ClaimRepository claimRepo,
              AuditEventRepository auditRepo) {

        if (claimRepo.count() > 0) {
            log.info("Seed data already present ({} claims) — skipping", claimRepo.count());
            return;
        }

        Random random = new Random(20260826L);

        List<Product> productList = List.of(
                product("AUTO-STD", "Personal Auto", "AUTO"),
                product("HOME-STD", "Homeowners", "PROPERTY"),
                product("RENT-STD", "Renters", "PROPERTY"),
                product("UMBR-STD", "Personal Umbrella", "LIABILITY"));
        productRepo.saveAll(productList);

        List<Adjuster> adjusterList = new ArrayList<>();
        for (String name : new String[]{"Dana Whitfield", "Marcus Bell", "Priya Raghunathan",
                "Tomasz Lewandowski", "Grace Achebe"}) {
            Adjuster adjuster = new Adjuster();
            adjuster.setName(name);
            adjuster.setEmail(name.toLowerCase().replace(' ', '.') + "@helix-insurance.example");
            adjuster.setLicenseNumber("ADJ-" + (10_000 + random.nextInt(89_999)));
            adjusterList.add(adjuster);
        }
        adjusterRepo.saveAll(adjusterList);

        List<Claimant> claimantList = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            String first = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String last = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String[] city = CITIES[random.nextInt(CITIES.length)];

            Claimant claimant = new Claimant();
            claimant.setFirstName(first);
            claimant.setLastName(last);
            claimant.setEmail(("%s.%s%d@example.com").formatted(
                    first.toLowerCase(), last.toLowerCase(), i));
            claimant.setPhone("(%03d) 555-%04d".formatted(200 + random.nextInt(700), random.nextInt(10_000)));
            claimant.setCity(city[0]);
            claimant.setState(city[1]);
            claimantList.add(claimant);
        }
        claimantRepo.saveAll(claimantList);

        List<Policy> policyList = new ArrayList<>();
        for (int i = 0; i < 140; i++) {
            Product product = productList.get(random.nextInt(productList.size()));
            Claimant holder = claimantList.get(random.nextInt(claimantList.size()));
            LocalDate effective = LocalDate.now().minusDays(120 + random.nextInt(400));

            Policy policy = new Policy();
            policy.setPolicyNumber("POL-%d-%06d".formatted(effective.getYear(), 100_000 + i * 137));
            policy.setProduct(product);
            policy.setProductName(product.getName());
            policy.setHolder(holder);
            policy.setStatus(i % 11 == 0 ? PolicyStatus.LAPSED : PolicyStatus.ACTIVE);
            policy.setEffectiveDate(effective);
            policy.setExpirationDate(effective.plusYears(1));
            policy.setPremiumAmount(BigDecimal.valueOf(600 + random.nextInt(3400)));

            for (String[] spec : coverageSpecsFor(product.getCode())) {
                Coverage coverage = new Coverage();
                coverage.setCode(spec[0]);
                coverage.setName(spec[1]);
                coverage.setLimitAmount(new BigDecimal(spec[2]));
                coverage.setDeductibleAmount(new BigDecimal(spec[3]));
                policy.addCoverage(coverage);
            }

            InsuredAsset asset = new InsuredAsset();
            asset.setAssetType(product.getLineOfBusiness().equals("AUTO") ? "VEHICLE" : "DWELLING");
            asset.setDescription(product.getLineOfBusiness().equals("AUTO")
                    ? "2021 Toyota RAV4 XLE" : "Single-family dwelling, 3 bed");
            asset.setIdentifier(product.getLineOfBusiness().equals("AUTO")
                    ? "JTMR%011d".formatted(random.nextInt(1_000_000)) : "APN-%08d".formatted(random.nextInt(10_000_000)));
            asset.setInsuredValue(BigDecimal.valueOf(25_000 + random.nextInt(400_000)));
            asset.setCity(holder.getCity());
            asset.setState(holder.getState());
            asset.setPolicy(policy);
            policy.getInsuredAssets().add(asset);

            policyList.add(policy);
        }
        policyRepo.saveAll(policyList);

        List<Policy> activePolicies = policyList.stream()
                .filter(p -> p.getStatus() == PolicyStatus.ACTIVE)
                .toList();

        ClaimStatus[] statuses = ClaimStatus.values();
        List<Claim> claimList = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            Policy policy = activePolicies.get(random.nextInt(activePolicies.size()));
            LocalDate incident = policy.getEffectiveDate().plusDays(random.nextInt(300));
            if (incident.isAfter(LocalDate.now())) {
                incident = LocalDate.now().minusDays(random.nextInt(30) + 1);
            }

            Claim claim = new Claim();
            claim.setClaimNumber("CLM-%d-%06d".formatted(LocalDate.now().getYear(), 4000 + i * 73));
            claim.setPolicy(policy);
            // The claimant is not always the policyholder: passengers, named drivers and
            // third parties all file against a policy they do not own.
            claim.setClaimant(random.nextInt(10) < 3
                    ? policy.getHolder()
                    : claimantList.get(random.nextInt(claimantList.size())));
            claim.setStatus(statuses[random.nextInt(statuses.length)]);
            claim.setIncidentDate(incident);
            claim.setSubmittedAt(incident.plusDays(1 + random.nextInt(5))
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
            claim.setLossType(LOSS_TYPES[random.nextInt(LOSS_TYPES.length)]);
            claim.setDescription("Reported loss on " + incident + " — "
                    + "initial notice taken by first notice of loss team.");

            if (random.nextInt(10) < 7) {
                claim.setAdjuster(adjusterList.get(random.nextInt(adjusterList.size())));
            }
            if (claim.getStatus().isTerminal()) {
                claim.setClosedAt(claim.getSubmittedAt().plus(5 + random.nextInt(40), ChronoUnit.DAYS));
            }

            int lineCount = 1 + random.nextInt(3);
            for (int l = 0; l < lineCount; l++) {
                Coverage coverage = policy.getCoverages().get(random.nextInt(policy.getCoverages().size()));
                ClaimLine line = new ClaimLine();
                line.setLineNumber(l + 1);
                line.setCoverage(coverage);
                line.setCoverageCode(coverage.getCode());
                line.setDescription(coverage.getName() + " — assessed damage");
                line.setClaimedAmount(BigDecimal.valueOf(400 + random.nextInt(14_000)));
                line.setStatus(ClaimLineStatus.values()[random.nextInt(ClaimLineStatus.values().length)]);
                claim.addLine(line);
            }

            ClaimDocument document = new ClaimDocument();
            document.setFileName("fnol-" + claim.getClaimNumber().toLowerCase() + ".pdf");
            document.setContentType("application/pdf");
            document.setSizeBytes(48_000 + random.nextInt(400_000));
            document.setDocumentType("FIRST_NOTICE_OF_LOSS");
            document.setStorageKey("claims/" + claim.getClaimNumber() + "/fnol.pdf");
            document.setUploadedBy("intake@helix-insurance.example");
            claim.addDocument(document);

            claimList.add(claim);
        }
        claimRepo.saveAll(claimList);

        List<AuditEvent> events = new ArrayList<>();
        claimList.forEach(claim -> events.add(AuditEvent.of(
                "Claim", claim.getId(), "CREATED", "intake@helix-insurance.example",
                "Claim opened with " + claim.getLines().size() + " line(s)")));
        auditRepo.saveAll(events);

        log.info("Seeded {} products, {} claimants, {} adjusters, {} policies, {} claims",
                productList.size(), claimantList.size(), adjusterList.size(),
                policyList.size(), claimList.size());
    }

    private static Product product(String code, String name, String lineOfBusiness) {
        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setLineOfBusiness(lineOfBusiness);
        product.setDescription(name + " coverage written on the " + lineOfBusiness + " book");
        return product;
    }

    private static String[][] coverageSpecsFor(String productCode) {
        return switch (productCode) {
            case "AUTO-STD" -> new String[][]{
                    {"COLL", "Collision", "50000.00", "1000.00"},
                    {"COMP", "Comprehensive", "50000.00", "500.00"},
                    {"BI", "Bodily Injury Liability", "300000.00", "0.00"},
                    {"PD", "Property Damage Liability", "100000.00", "0.00"}};
            case "HOME-STD" -> new String[][]{
                    {"DWELL", "Dwelling", "450000.00", "2500.00"},
                    {"CONTENT", "Personal Property", "180000.00", "1000.00"},
                    {"LOSSUSE", "Loss of Use", "45000.00", "0.00"},
                    {"LIAB", "Personal Liability", "300000.00", "0.00"}};
            case "RENT-STD" -> new String[][]{
                    {"CONTENT", "Personal Property", "60000.00", "500.00"},
                    {"LIAB", "Personal Liability", "100000.00", "0.00"}};
            default -> new String[][]{
                    {"UMB", "Umbrella Liability", "1000000.00", "0.00"}};
        };
    }
}
