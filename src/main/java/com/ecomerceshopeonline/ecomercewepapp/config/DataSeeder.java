package com.ecomerceshopeonline.ecomercewepapp.config;

import com.ecomerceshopeonline.ecomercewepapp.entity.Role;
import com.ecomerceshopeonline.ecomercewepapp.entity.User;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.AuthProvider;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.RoleName;
import com.ecomerceshopeonline.ecomercewepapp.repository.RoleRepository;
import com.ecomerceshopeonline.ecomercewepapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Makes sure both roles exist and that there is at least one administrator to sign in as.
 * Idempotent - safe to run on every startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoles();
        seedAdmin();
    }

    private void seedRoles() {
        for (RoleName name : RoleName.values()) {
            roleRepository.findByName(name).orElseGet(() -> {
                log.info("Seeding role {}", name);
                return roleRepository.save(Role.builder()
                        .name(name)
                        .description(switch (name) {
                            case ROLE_ADMIN -> "Full access to the admin dashboard";
                            case ROLE_CUSTOMER -> "Can shop, order and pay";
                        })
                        .build());
            });
        }
    }

    private void seedAdmin() {
        String email = properties.admin().email().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN was not seeded"));

        User admin = User.builder()
                .fullName(properties.admin().fullName())
                .email(email)
                .password(passwordEncoder.encode(properties.admin().password()))
                .provider(AuthProvider.LOCAL)
                .emailVerified(true)
                .enabled(true)
                .build();
        admin.addRole(adminRole);
        userRepository.save(admin);

        log.warn("Seeded default administrator '{}'. Change ADMIN_PASSWORD before deploying.", email);
    }
}
