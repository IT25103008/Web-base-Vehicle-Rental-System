package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ClaimResponse {

    private int claimId;
    private int damageReportId;
    private String insuranceProvider;
    private BigDecimal claimAmount;
    private LocalDate submittedDate;
    private String status;

    public ClaimResponse() { }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
