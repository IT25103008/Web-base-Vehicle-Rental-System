package com.vehiclerental.model;

import com.vehiclerental.enums.HandoverStatus;
import com.vehiclerental.enums.HandoverType;

import java.time.LocalDateTime;

public class Handover {

    private int handoverId;
    private int bookingId;
    private HandoverType handoverType;              // PICKUP or RETURN (partial key in EER)
    private LocalDateTime handoverDate;
    private Integer processedByStaffId;
    private Integer mileageAtEvent;
    private String fuelLevel;
    private String conditionNotes;
    private HandoverStatus status;

    public Handover() {
    }

    public int getHandoverId() { return handoverId; }
    public void setHandoverId(int handoverId) { this.handoverId = handoverId; }

    public int getBookingId() { return bookingId; }
    public void setBookingId(int bookingId) { this.bookingId = bookingId; }

    public HandoverType getHandoverType() { return handoverType; }
    public void setHandoverType(HandoverType handoverType) { this.handoverType = handoverType; }

    public LocalDateTime getHandoverDate() { return handoverDate; }
    public void setHandoverDate(LocalDateTime handoverDate) { this.handoverDate = handoverDate; }

    public Integer getProcessedByStaffId() { return processedByStaffId; }
    public void setProcessedByStaffId(Integer processedByStaffId) { this.processedByStaffId = processedByStaffId; }

    public Integer getMileageAtEvent() { return mileageAtEvent; }
    public void setMileageAtEvent(Integer mileageAtEvent) { this.mileageAtEvent = mileageAtEvent; }

    public String getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(String fuelLevel) { this.fuelLevel = fuelLevel; }

    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = conditionNotes; }

    public HandoverStatus getStatus() { return status; }
    public void setStatus(HandoverStatus status) { this.status = status; }
}
