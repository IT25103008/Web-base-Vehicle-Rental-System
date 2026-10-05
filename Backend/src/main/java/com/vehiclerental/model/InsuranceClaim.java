package com.vehiclerental.model;

import com.vehiclerental.enums.ClaimStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

public class InsuranceClaim {

    private int claimId;
    private int damageReportId;
    private String insuranceProvider;
    private BigDecimal claimAmount;
    private LocalDate submittedDate;
    private ClaimStatus status;

    public InsuranceClaim() {
    }

    public int getClaimId() { return claimId; }
    public void setClaimId(int claimId) { this.claimId = claimId; }

    public int getDamageReportId() { return damageReportId; }
    public void setDamageReportId(int damageReportId) { this.damageReportId = damageReportId; }

    public String getInsuranceProvider() { return insuranceProvider; }
    public void setInsuranceProvider(String insuranceProvider) { this.insuranceProvider = insuranceProvider; }

    public BigDecimal getClaimAmount() { return claimAmount; }
    public void setClaimAmount(BigDecimal claimAmount) { this.claimAmount = claimAmount; }

    public LocalDate getSubmittedDate() { return submittedDate; }
    public void setSubmittedDate(LocalDate submittedDate) { this.submittedDate = submittedDate; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }
}
