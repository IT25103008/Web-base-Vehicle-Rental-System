package com.vehiclerental.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {

    private int paymentId;
    private int bookingId;
    private BigDecimal amount;
    private String status;
    private Integer updatedBy;
    private LocalDateTime updatedAt;
    private BigDecimal refundAmount;
    private String note;

    public PaymentResponse() { }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Integer updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
