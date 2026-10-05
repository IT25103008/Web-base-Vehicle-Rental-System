package com.vehiclerental.config;

import com.vehiclerental.util.AppClock;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/** Points the application clock at rental.zone (see AppClock). */
@Configuration
public class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    private final String zone;

    public ClockConfig(@Value("${rental.zone:" + AppClock.DEFAULT_ZONE + "}") String zone) {
        this.zone = zone;
    }

    @PostConstruct
    void apply() {
        AppClock.setZone(ZoneId.of(zone));
        log.info("Business day is kept in {} (today is {})", zone, AppClock.today());
    }
}
