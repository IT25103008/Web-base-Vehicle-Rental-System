package com.vehiclerental.service;

/**
 * The reminders that used to exist only as queries nobody ran.
 *
 * Expiring insurance, services falling due, cars that are late back and
 * bookings nobody collected are all things somebody has to be told about —
 * the /reminders and /expiring endpoints only helped if a member of staff
 * happened to look at them.
 */
public interface ReminderService {

    /** Everything below, in one pass. Runs daily and can be triggered by an administrator. */
    ReminderSummary runDailyChecks();

    /** What the last pass found, for the response and the log. */
    class ReminderSummary {
        private int policiesExpired;
        private int policiesExpiringSoon;
        private int overdueReturns;
        private int missedPickups;
        private int returnsDueToday;
        private int maintenanceDue;
        private int noShowsClosed;

        public int getPoliciesExpired() { return policiesExpired; }
        public void setPoliciesExpired(int v) { this.policiesExpired = v; }

        public int getPoliciesExpiringSoon() { return policiesExpiringSoon; }
        public void setPoliciesExpiringSoon(int v) { this.policiesExpiringSoon = v; }

        public int getOverdueReturns() { return overdueReturns; }
        public void setOverdueReturns(int v) { this.overdueReturns = v; }

        public int getNoShowsClosed() { return noShowsClosed; }
        public void setNoShowsClosed(int v) { this.noShowsClosed = v; }

        public int getMissedPickups() { return missedPickups; }
        public void setMissedPickups(int v) { this.missedPickups = v; }

        public int getReturnsDueToday() { return returnsDueToday; }
        public void setReturnsDueToday(int v) { this.returnsDueToday = v; }

        public int getMaintenanceDue() { return maintenanceDue; }
        public void setMaintenanceDue(int v) { this.maintenanceDue = v; }
    }
}
