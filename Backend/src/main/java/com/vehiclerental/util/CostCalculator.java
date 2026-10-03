package com.vehiclerental.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class CostCalculator {

    // Private constructor: this class is only a bag of static methods.
    private CostCalculator() {
    }

    /**
     * Number of rental days between pickup and return (nights counted,
     * e.g. 15th -> 18th = 3 days). A same-day rental counts as 1 day.
     */
    public static long rentalDays(LocalDate pickup, LocalDate returnDate) {
        long days = ChronoUnit.DAYS.between(pickup, returnDate);
        if (days < 1) {
            return 1;
        }
        return days;
    }

    /**
     * Base cost = days * daily rate, rounded to 2 decimal places.
     */
    public static BigDecimal totalCost(BigDecimal dailyRate, LocalDate pickup, LocalDate returnDate) {
        long days = rentalDays(pickup, returnDate);
        return dailyRate
                .multiply(BigDecimal.valueOf(days))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** How many days late the vehicle came back (0 if on time or early). */
    public static long lateDays(LocalDate bookedReturn, LocalDate actualReturn) {
        if (bookedReturn == null || actualReturn == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(bookedReturn, actualReturn);
        return days > 0 ? days : 0;
    }

    /**
     * Late-return surcharge: each extra day is charged at the daily rate
     * multiplied by {@link RentalPolicy#LATE_RETURN_MULTIPLIER}.
     */
    public static BigDecimal lateFee(BigDecimal dailyRate, long lateDays) {
        if (lateDays <= 0 || dailyRate == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return dailyRate
                .multiply(BigDecimal.valueOf(lateDays))
                .multiply(RentalPolicy.LATE_RETURN_MULTIPLIER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * What the customer actually owes once the vehicle is back:
     * base rental + late-return surcharge + damage charge, never below zero.
     */
    public static BigDecimal finalCost(BigDecimal baseCost, BigDecimal lateFee, BigDecimal damageCharge) {
        BigDecimal total = nz(baseCost).add(nz(lateFee)).add(nz(damageCharge));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Subtract an approved insurance payout from what the customer owes. */
    public static BigDecimal applyCredit(BigDecimal amount, BigDecimal credit) {
        BigDecimal result = nz(amount).subtract(nz(credit));
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            result = BigDecimal.ZERO;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
