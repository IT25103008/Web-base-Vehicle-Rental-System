// ============================================================
// Input rules, mirrored from the backend (validation/Rules.java).
// The server has the final say; these exist so a mistake is caught while
// the person is still looking at the box, with the same wording the server
// would use. Change both files together.
// ============================================================

export const LIMITS = {
  name: 80, email: 120, phone: 20, address: 255, licence: 50, code: 30, position: 80,
  passwordMin: 8, passwordMax: 72,
  notes: 500, reason: 255, provider: 120, policyNumber: 50,
  branchName: 120, street: 120, city: 80,
  plate: 20, model: 80, fuelType: 30, search: 100,
  money: 99999999.99, mileage: 10000000,
};

/** Format rules. `re` is tested against the trimmed value; empty optional boxes are skipped. */
export const RULES = {
  phone: {
    re: /^(\+94|0)[ -]?\d{2}[ -]?\d{3}[ -]?\d{4}$/,
    msg: 'Enter a Sri Lankan phone number, e.g. 077 123 4567 or +94 77 123 4567',
    inputmode: 'tel', placeholder: '077 123 4567',
  },
  licence: {
    re: /^[A-Z]\d{7}$|^\d{8,12}$/i,
    msg: 'Enter the licence number as printed on the card, e.g. B1234567',
    upper: true, placeholder: 'B1234567',
  },
  plate: {
    re: /^(?:[A-Z]{2}[ -])?(?:[A-Z]{1,3}|\d{1,3})[ -]?\d{4}$/i,
    msg: 'Enter a plate like CAB-1234, WP CAB-1234 or 301-1234',
    upper: true, placeholder: 'CAB-1234',
  },
  name: {
    re: /^\p{L}[\p{L} .'-]*$/u,
    msg: 'Use letters, spaces, apostrophes or hyphens only',
  },
  code: {
    re: /^[A-Za-z0-9-]{2,30}$/,
    msg: 'Use 2 to 30 letters, digits or hyphens',
  },
  policy: {
    re: /^[A-Za-z0-9/-]{3,50}$/,
    msg: 'Use 3 to 50 letters, digits, slashes or hyphens',
    upper: true,
  },
  otp: {
    re: /^\d{6}$/,
    msg: 'Enter the six-digit code from your app',
    inputmode: 'numeric', strip: /\s/g,
  },
};

/** The 25 administrative districts of Sri Lanka (same list as the backend). */
export const DISTRICTS = [
  'Ampara', 'Anuradhapura', 'Badulla', 'Batticaloa', 'Colombo', 'Galle', 'Gampaha',
  'Hambantota', 'Jaffna', 'Kalutara', 'Kandy', 'Kegalle', 'Kilinochchi', 'Kurunegala',
  'Mannar', 'Matale', 'Matara', 'Monaragala', 'Mullaitivu', 'Nuwara Eliya', 'Polonnaruwa',
  'Puttalam', 'Ratnapura', 'Trincomalee', 'Vavuniya',
];

/**
 * Defaults by field name, applied by ui.js › field() unless the form passes
 * its own. Field names are the JSON property names, so these line up with
 * the request classes on the server.
 */
export const FIELD_DEFAULTS = {
  firstName: { maxlength: LIMITS.name, rule: 'name', autocomplete: 'given-name' },
  lastName: { maxlength: LIMITS.name, rule: 'name', autocomplete: 'family-name' },
  email: { maxlength: LIMITS.email },
  phoneNumber: { maxlength: LIMITS.phone, rule: 'phone' },
  contactNumber: { maxlength: LIMITS.phone, rule: 'phone' },
  address: { maxlength: LIMITS.address },
  drivingLicenseNumber: { maxlength: LIMITS.licence, rule: 'licence' },
  employeeCode: { maxlength: LIMITS.code, rule: 'code' },
  adminCode: { maxlength: LIMITS.code, rule: 'code' },
  position: { maxlength: LIMITS.position },
  newPassword: { minlength: LIMITS.passwordMin, maxlength: LIMITS.passwordMax },
  currentPassword: { maxlength: LIMITS.passwordMax },
  confirm: { maxlength: LIMITS.passwordMax },
  code: { maxlength: 7, rule: 'otp' },

  specialRequests: { maxlength: LIMITS.notes, counter: true },
  conditionNotes: { maxlength: LIMITS.notes, counter: true },
  damageDescription: { maxlength: LIMITS.notes, counter: true },
  description: { maxlength: LIMITS.notes, counter: true },
  reason: { maxlength: LIMITS.reason, counter: true },

  repairType: { maxlength: LIMITS.provider },
  serviceProvider: { maxlength: LIMITS.provider },
  insuranceProvider: { maxlength: LIMITS.provider },
  provider: { maxlength: LIMITS.provider },
  policyNumber: { maxlength: LIMITS.policyNumber, rule: 'policy' },

  name: { maxlength: LIMITS.branchName },
  street: { maxlength: LIMITS.street },
  city: { maxlength: LIMITS.city },

  plateNumber: { maxlength: LIMITS.plate, rule: 'plate' },
  model: { maxlength: LIMITS.model },

  rentalPricePerDay: { min: 0.01, max: LIMITS.money, step: '0.01' },
  cost: { min: 0, max: LIMITS.money, step: '0.01' },
  claimAmount: { min: 0.01, max: LIMITS.money, step: '0.01' },
  damageEstimatedCost: { min: 0, max: LIMITS.money, step: '0.01' },
  estimatedRepairCost: { min: 0, max: LIMITS.money, step: '0.01' },
  mileage: { min: 0, max: LIMITS.mileage },
  mileageAtEvent: { min: 0, max: LIMITS.mileage },
  returnMileage: { min: 0, max: LIMITS.mileage },
  passengerCapacity: { min: 1, max: 60 },
  manufactureYear: { min: 1950, max: new Date().getFullYear() + 1 },
};

/** 2026-09-29 -> 29 Sept 2026, as dates are shown everywhere else. */
function prettyDate(iso) {
  const d = new Date(`${iso}T00:00:00`);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Today in the browser's local time, as yyyy-mm-dd (the form of a date input's value). */
function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * The message for one control, or '' when it is fine. Checks the browser's
 * own constraints (required, lengths, ranges, email) and then any data-rule.
 */
export function problemWith(el) {
  if (el.disabled || el.type === 'hidden' || el.type === 'file') return '';
  const v = el.validity;
  const label = el.closest('.field')?.querySelector('.field__label')?.textContent.replace(/\s*\*$/, '').trim() || 'This field';
  if (v.valueMissing) return el.tagName === 'SELECT' ? `Choose ${label.toLowerCase()}` : `${label} is required`;
  if (v.typeMismatch && el.type === 'email') return 'Enter a valid email address';
  if (v.badInput) return el.type === 'number' ? 'Enter a number' : `Check ${label.toLowerCase()}`;
  if (v.tooShort) return `${label} must be at least ${el.minLength} characters`;
  if (v.tooLong) return `${label} can be at most ${el.maxLength} characters`;
  if (v.rangeUnderflow && el.dataset.afterMsg) return el.dataset.afterMsg;
  if (v.rangeUnderflow && el.dataset.after && el.form) {
    const src = el.dataset.after.split('|').map((n) => el.form.elements[n]).find((s) => s && s.value);
    const srcLabel = src?.closest('.field')?.querySelector('.field__label')?.textContent.replace(/\s*\*$/, '').trim().toLowerCase();
    if (srcLabel) return `${label} must be ${el.hasAttribute('data-after-strict') ? 'after' : 'on or after'} ${srcLabel}`;
  }
  if (v.rangeUnderflow) {
    if (el.type === 'date') return el.min === today() ? `${label} cannot be in the past` : `${label} cannot be before ${prettyDate(el.min)}`;
    return `${label} must be at least ${el.type === 'time' ? el.min : Number(el.min).toLocaleString('en-GB')}`;
  }
  if (v.rangeOverflow) {
    if (el.type === 'date') return el.max === today() ? `${label} cannot be in the future` : `${label} cannot be after ${prettyDate(el.max)}`;
    return `${label} can be at most ${el.type === 'time' ? el.max : Number(el.max).toLocaleString('en-GB')}`;
  }
  if (v.stepMismatch) return el.type === 'number' ? `${label} can have at most two decimal places` : `Check ${label.toLowerCase()}`;
  const rule = RULES[el.dataset.rule];
  const value = (el.value || '').trim();
  if (rule && value) {
    const tidy = rule.strip ? value.replace(rule.strip, '') : value;
    if (!rule.re.test(tidy)) return rule.msg;
  }
  if (!v.valid) return el.validationMessage || `Check ${label.toLowerCase()}`;
  return '';
}
