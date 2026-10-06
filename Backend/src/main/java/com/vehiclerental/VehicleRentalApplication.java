package com.vehiclerental;

import com.vehiclerental.util.AppClock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling   // drives the daily reminder run (see ReminderServiceImpl)
public class VehicleRentalApplication {

    public static void main(String[] args) {
        // The JVM's own default zone decides how the MySQL driver converts
        // timestamps, and when the 07:00 reminder cron fires. Pin it to the
        // business zone before anything starts, so neither depends on how the
        // server happens to be set up. (RENTAL_ZONE overrides it.)
        String zone = System.getenv().getOrDefault("RENTAL_ZONE", AppClock.DEFAULT_ZONE);
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        SpringApplication.run(VehicleRentalApplication.class, args);
    }
}
