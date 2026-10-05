package com.vehiclerental.config;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Recovers from a migration that stopped half way.
 *
 * MySQL cannot roll back a table change, so a failed migration leaves a
 * "failed" row in flyway_schema_history, and Flyway then refuses to start at
 * all until someone runs "flyway repair" by hand. Our migrations check before
 * each change and are safe to run again, so the failed row is cleared and the
 * migration simply re-runs.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    public FlywayMigrationStrategy repairFailedThenMigrate() {
        return flyway -> {
            MigrationInfo[] failed = Arrays.stream(flyway.info().all())
                .filter(m -> m.getState() == MigrationState.FAILED)
                .toArray(MigrationInfo[]::new);
            if (failed.length > 0) {
                for (MigrationInfo m : failed) {
                    log.warn("Migration V{} ({}) failed on an earlier start; clearing it and running it again",
                             m.getVersion(), m.getDescription());
                }
                flyway.repair();
            }
            flyway.migrate();
        };
    }
}
