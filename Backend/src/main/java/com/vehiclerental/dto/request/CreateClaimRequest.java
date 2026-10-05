package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CreateClaimRequest {

    @NotNull(message = "Choose a damage report")
    @Positive(message = "Choose a damage report")
    private Integer damageReportId;

    @NotBlank(message = "Insurance provider is required")
    @Size(max = Rules.PROVIDER, message = "Insurance provider can be at most " + Rules.PROVIDER + " characters")
    private String insuranceProvider;

    @NotNull(message = "Claim amount is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Claim amount must be more than zero")
    @DecimalMax(value = Rules.MONEY_MAX, message = "Claim amount is too large")
    private BigDecimal claimAmount;

    public CreateClaimRequest() { }

    public Integer getDamageReportId() { return damageReportId; }
    public void setDamageReportId(Integer damageReportId) { this.damageReportId = damageReportId; }

    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = Rules.clean(insuranceProvider); }

    public BigDecimal getClaimAmount() { return claimAmount; }
    public void setClaimAmount(BigDecimal claimAmount) { this.claimAmount = claimAmount; }
}
