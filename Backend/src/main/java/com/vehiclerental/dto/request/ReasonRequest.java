package com.vehiclerental.dto.request;

import com.vehiclerental.validation.Rules;
import jakarta.validation.constraints.Size;

/**
 * A free-text reason, sent in the request body. Reasons used to travel as
 * ?reason=... query parameters, which put them in access logs and browser
 * history; the body keeps them out of both.
 */
public class ReasonRequest {

    @Size(max = Rules.REASON, message = "Reason can be at most " + Rules.REASON + " characters")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = Rules.clean(reason); }
}
