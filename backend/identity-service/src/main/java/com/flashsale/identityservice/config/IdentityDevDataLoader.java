package com.flashsale.identityservice.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commonlib.config.DevDataProperties;
import com.flashsale.identityservice.domain.repository.AddressRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Seeds FE test-dataset users (900001-900003) with roles and addresses for local development.
 * Password for ALL dev accounts is: {@code dev123}
 *
 * <ul>
 *   <li>900001 = fe_buyer (BUYER)</li>
 *   <li>900002 = fe_seller (SELLER)</li>
 *   <li>900003 = fe_admin (ADMIN)</li>
 * </ul>
 *
 * Reset: set dev-data.reset=true in identity-service's application-dev.yml
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "dev-data.enabled", havingValue = "true", matchIfMissing = false)
public class IdentityDevDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final DevDataProperties devDataProperties;
    private final JdbcTemplate jdbcTemplate;

    private static final String JSON_PATH = "test-data/users.json";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[IdentityDevDataLoader] Starting dev data seed for identity-service...");

        if (devDataProperties.isReset()) {
            log.warn("[IdentityDevDataLoader] RESET=true — wiping all identity data...");
            addressRepository.deleteAllInBatch();
            roleRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
            log.info("[IdentityDevDataLoader] All identity data wiped.");
        }

        seedFeData();
        seedFromJsonDataset();

        log.info("[IdentityDevDataLoader] Dev data seed complete.");
    }

    /**
     * Seeds FE test-dataset users (900001-900003) with roles and addresses.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFeData() {
        log.info("[IdentityDevDataLoader] Seeding FE test-dataset...");

        String hashed = passwordEncoder.encode("dev123");

        // ──────────────────────────────────────────────
        // 1. Users
        // ──────────────────────────────────────────────
        jdbcTemplate.update("""
            INSERT INTO identity.users (id, username, email, phone, password, full_name, status, role, avatar_url, last_login_at, notification_preferences, created_at, updated_at)
            VALUES
                (900001, 'fe_buyer',  'fe_buyer@example.test',  '0999000001', ?, 'Nguyễn Văn Mua',  'ACTIVE', 'BUYER',  'https://picsum.photos/seed/fe-buyer-avatar/200/200', now() - interval '2 hours', '{"email":true,"sms":false,"push":true}'::jsonb,  now() - interval '20 days', now()),
                (900002, 'fe_seller', 'fe_seller@example.test', '0999000002', ?, 'Trần Thị Bán',    'ACTIVE', 'SELLER', 'https://picsum.photos/seed/fe-seller-avatar/200/200', now() - interval '1 hour',  '{"email":true,"sms":true,"push":true}'::jsonb,   now() - interval '19 days', now()),
                (900003, 'fe_admin',  'fe_admin@example.test',  '0999000003', ?, 'Lê Admin',         'ACTIVE', 'ADMIN',  'https://picsum.photos/seed/fe-admin-avatar/200/200', now() - interval '30 minutes', '{"email":true,"sms":false,"push":false}'::jsonb, now() - interval '18 days', now())
            ON CONFLICT (id) DO UPDATE SET
                username                 = EXCLUDED.username,
                email                    = EXCLUDED.email,
                phone                    = EXCLUDED.phone,
                password                 = EXCLUDED.password,
                full_name                = EXCLUDED.full_name,
                status                   = EXCLUDED.status,
                role                     = EXCLUDED.role,
                avatar_url               = EXCLUDED.avatar_url,
                last_login_at            = EXCLUDED.last_login_at,
                notification_preferences = EXCLUDED.notification_preferences,
                updated_at               = now()
            """, hashed, hashed, hashed);

        // ──────────────────────────────────────────────
        // 2. Roles
        // ──────────────────────────────────────────────
        jdbcTemplate.update("""
            INSERT INTO identity.roles (id, user_id, role_name, created_at, updated_at)
            VALUES
                (900001, 900001, 'BUYER',  now() - interval '20 days', now()),
                (900002, 900002, 'SELLER', now() - interval '19 days', now()),
                (900003, 900003, 'ADMIN',  now() - interval '18 days', now())
            ON CONFLICT (id) DO UPDATE SET
                user_id    = EXCLUDED.user_id,
                role_name  = EXCLUDED.role_name,
                updated_at = now()
            """);

        // ──────────────────────────────────────────────
        // 3. Addresses
        // ──────────────────────────────────────────────
        // 900001 (fe_buyer):  3 addresses — primary in HCMC, secondary in Hanoi, office in Binh Duong
        // 900002 (fe_seller): 2 addresses — warehouse in HCMC, return center in Hanoi
        // 900003 (fe_admin):  3 addresses — home in Hanoi, office in HCMC, warehouse in Da Nang
        jdbcTemplate.update("""
            INSERT INTO identity.addresses (id, user_id, province_id, district_id, full_address, is_default, created_at, updated_at)
            VALUES
                (900001, 900001, 79, 1, '123 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',    true,  now() - interval '20 days', now()),
                (900002, 900001,  1, 1, '456 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội',        false, now() - interval '19 days', now()),
                (900003, 900002, 79, 3, '789 Lê Văn Sỹ, Quận 3, TP. Hồ Chí Minh',      true,  now() - interval '18 days', now()),
                (900004, 900003,  1, 4, '321 Kim Mã, Ba Đình, Hà Nội',                  true,  now() - interval '17 days', now()),
                (900005, 900002,  1, 5, '654 Nguyễn Chí Thanh, Đống Đa, Hà Nội',        false, now() - interval '16 days', now()),
                (900006, 900001, 79, 7, '789 Phạm Văn Đồng, Thủ Đức, TP. Hồ Chí Minh',  false, now() - interval '15 days', now()),
                (900007, 900003, 79, 1, '12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh',       false, now() - interval '14 days', now()),
                (900008, 900003, 48, 1, '456 Bạch Đằng, Hải Châu, Đà Nẵng',             false, now() - interval '13 days', now())
            ON CONFLICT (id) DO UPDATE SET
                user_id      = EXCLUDED.user_id,
                province_id  = EXCLUDED.province_id,
                district_id  = EXCLUDED.district_id,
                full_address = EXCLUDED.full_address,
                is_default   = EXCLUDED.is_default,
                updated_at   = now()
            """);

        // ──────────────────────────────────────────────
        // 4. Reset sequences
        // ──────────────────────────────────────────────
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.users_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.users), 900003))",
            Long.class);
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.roles_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.roles), 900003))",
            Long.class);
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.addresses_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.addresses), 900008))",
            Long.class);

        log.info("[IdentityDevDataLoader] FE test-dataset seeded (3 users, 3 roles, 8 addresses).");
    }

    /**
     * Seeds users from {@code test-data/users.json} (the full-coverage dataset).
     * Covers all 13 users (sellers, buyers, admin) with their addresses and roles.
     * Idempotent via ON CONFLICT DO UPDATE.
     */
    private void seedFromJsonDataset() {
        List<Map<String, Object>> users;
        try (InputStream is = new ClassPathResource(JSON_PATH).getInputStream()) {
            users = new ObjectMapper().readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            log.warn("[IdentityDevDataLoader] Could not read {} — skipping JSON seed: {}", JSON_PATH, e.getMessage());
            return;
        }

        log.info("[IdentityDevDataLoader] Seeding {} users from {}", users.size(), JSON_PATH);

        // Check if JSON data already exists (by checking first user)
        Integer existing = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM identity.users WHERE id = ?", Integer.class, (Integer) users.getFirst().get("id"));
        if (existing != null && existing > 0) {
            log.info("[IdentityDevDataLoader] JSON dataset already seeded, skipping.");
            return;
        }

        String hashed = passwordEncoder.encode("dev123");
        int maxUserId = 0;
        int maxAddressId = 0;
        int addrSeq = 900009; // start after FE fixture addresses (1-900008)

        for (Map<String, Object> user : users) {
            int id = (int) user.get("id");
            String username = (String) user.get("username");
            String email = (String) user.get("email");
            String phone = (String) user.get("phone");
            String fullName = (String) user.get("fullName");
            String role = (String) user.get("role");
            String status = (String) user.get("status");

            maxUserId = Math.max(maxUserId, id);

            // Insert user
            jdbcTemplate.update("""
                INSERT INTO identity.users (id, username, email, phone, password, full_name, status, role, avatar_url, last_login_at, notification_preferences, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now() - interval '1 hour', '{"email":true,"sms":false,"push":true}'::jsonb, now() - interval '15 days', now())
                ON CONFLICT (id) DO UPDATE SET
                    username = EXCLUDED.username, email = EXCLUDED.email, phone = EXCLUDED.phone,
                    password = EXCLUDED.password, full_name = EXCLUDED.full_name,
                    status = EXCLUDED.status, role = EXCLUDED.role, updated_at = now()
                """, id, username, email, phone, hashed, fullName, status, role);

            // Insert role
            jdbcTemplate.update("""
                INSERT INTO identity.roles (id, user_id, role_name, created_at, updated_at)
                VALUES (?, ?, ?, now() - interval '15 days', now())
                ON CONFLICT (id) DO UPDATE SET
                    user_id = EXCLUDED.user_id, role_name = EXCLUDED.role_name, updated_at = now()
                """, id, id, role);

            // Insert addresses
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> addresses = (List<Map<String, Object>>) user.get("addresses");
            if (addresses != null) {
                for (Map<String, Object> addr : addresses) {
                    jdbcTemplate.update("""
                        INSERT INTO identity.addresses (id, user_id, province_id, district_id, full_address, is_default, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, now() - interval '15 days', now())
                        ON CONFLICT (id) DO UPDATE SET
                            user_id = EXCLUDED.user_id, province_id = EXCLUDED.province_id,
                            district_id = EXCLUDED.district_id, full_address = EXCLUDED.full_address,
                            is_default = EXCLUDED.is_default, updated_at = now()
                        """, addrSeq, id,
                        addr.get("provinceId"), addr.get("districtId"),
                        addr.get("fullAddress"), addr.get("isDefault"));
                    maxAddressId = Math.max(maxAddressId, addrSeq);
                    addrSeq++;
                }
            }
        }

        // Reset sequences
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.users_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.users), ?))",
            Long.class, Math.max(maxUserId, 900003));
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.roles_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.roles), ?))",
            Long.class, Math.max(maxUserId, 900003));
        jdbcTemplate.queryForObject(
            "SELECT setval('identity.addresses_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM identity.addresses), ?))",
            Long.class, Math.max(maxAddressId, 900008));

        log.info("[IdentityDevDataLoader] JSON dataset seeded ({} users).", users.size());
    }
}
