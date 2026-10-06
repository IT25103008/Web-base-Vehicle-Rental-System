// ============================================================
// The pages behind the links in our emails - forgotten password,
// choosing a new one, confirming an email address - and the privacy
// notice every new customer accepts.
// ============================================================
import { api } from '../api.js';
import { store, loadUser } from '../store.js';
import { navigate } from '../app.js';
import { $, esc, icon, field, readForm, formError, toast, validateForm, showFieldErrors, errorSummary, markField } from '../ui.js';
import { authStage, footer } from './public.js';

function panel(el, { stage, eyebrow, title, body }) {
  el.innerHTML = `
    <section class="auth">
      ${stage}
      <div class="auth__panel">
        <header>
          <p class="eyebrow">${esc(eyebrow)}</p>
          <h1 class="title-lg" style="margin-top:12px">${esc(title)}</h1>
        </header>
        ${body}
      </div>
    </section>`;
}

function busy(btn, on) {
  btn.disabled = on;
  btn.classList.toggle('is-busy', on);
}

// ------------------------------------------------------------------
// Forgot password
// ------------------------------------------------------------------
export async function renderForgot({ el }) {
  panel(el, {
    stage: authStage('Locked out?<br>It happens.', 'Tell us the email you signed up with and we will send a link to choose a new password. It works once, for an hour.', 'login'),
    eyebrow: 'Password help',
    title: 'Reset your password',
    body: `<form class="auth__form" id="forgot" novalidate>
        ${field({ name: 'email', label: 'Email', type: 'email', required: true, autocomplete: 'email' })}
        <div id="forgot-msg"></div>
        <button class="btn btn--lg btn--block" type="submit">Send the link ${icon('arrowRight', 18)}</button>
        <p class="auth__switch">Remembered it? <a href="#/login">Sign in</a></p>
      </form>`,
  });
  const form = $('#forgot', el);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!validateForm(form)) return;
    const btn = form.querySelector('[type=submit]');
    const box = $('#forgot-msg', el);
    busy(btn, true);
    try {
      const r = await api.auth.forgotPassword(readForm(form).email);
      // Same answer whether or not the account exists, so this cannot be used to probe for accounts.
      box.innerHTML = `<div class="notice notice--green">${icon('mail', 18)}<span>${esc(r.message)}</span></div>`;
      form.elements.email.disabled = true;
      btn.hidden = true;
    } catch (err) {
      box.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      busy(btn, false);
    }
  });
}

// ------------------------------------------------------------------
// Choose a new password (from the email link)
// ------------------------------------------------------------------
export async function renderReset({ el, query }) {
  const token = query.token || '';
  panel(el, {
    stage: authStage('A fresh start.', 'Choose a new password. Every device signed in with the old one is signed out.', 'login'),
    eyebrow: 'Password help',
    title: 'Choose a new password',
    body: token ? `<form class="auth__form" id="reset" novalidate>
        ${field({ name: 'password', label: 'New password', type: 'password', required: true, minlength: 8, maxlength: 72, autocomplete: 'new-password', hint: 'At least 8 characters, with letters and numbers' })}
        ${field({ name: 'confirm', label: 'Type it again', type: 'password', required: true, autocomplete: 'new-password' })}
        <div id="reset-error"></div>
        <button class="btn btn--lg btn--block" type="submit">Save the new password ${icon('arrowRight', 18)}</button>
      </form>` : `<div class="notice notice--red">${icon('alert', 18)}<span>This link is incomplete. Open the link from your email again, or <a href="#/forgot">ask for a new one</a>.</span></div>`,
  });
  const form = $('#reset', el);
  if (!form) return;
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!validateForm(form)) return;
    const v = readForm(form);
    const box = $('#reset-error', el);
    box.innerHTML = '';
    if (v.password !== v.confirm) { markField(form.elements.confirm, 'The two passwords are not the same.'); form.elements.confirm.focus(); return; }
    const btn = form.querySelector('[type=submit]');
    busy(btn, true);
    try {
      const r = await api.auth.resetPassword(token, v.password);
      toast(r.message, 'success');
      navigate('/login');
    } catch (err) {
      box.innerHTML = formError(errorSummary(err, showFieldErrors(form, err)));
      busy(btn, false);
    }
  });
}

// ------------------------------------------------------------------
// Confirm an email address (from the email link)
// ------------------------------------------------------------------
export async function renderVerify({ el, query }) {
  panel(el, {
    stage: authStage('Nearly there.', 'Confirming your email means we can reach you about your bookings - and it is needed before your first reservation.', 'register'),
    eyebrow: 'Your account',
    title: 'Confirming your email',
    body: '<div id="verify-state" class="verify-state"><span class="spinner" aria-hidden="true"></span><p class="muted">One moment…</p></div>',
  });
  const box = $('#verify-state', el);
  if (!query.token) {
    box.innerHTML = `<div class="notice notice--red">${icon('alert', 18)}<span>This link is incomplete. Sign in and ask for a new one from your account page.</span></div>`;
    return;
  }
  try {
    await api.auth.verifyEmail(query.token);
    if (store.user) await loadUser();
    box.innerHTML = `
      <div class="verify-state__ok">${icon('check', 30)}</div>
      <p><b>Your email is confirmed.</b> Thank you.</p>
      <a class="btn btn--lg" href="#/${store.user ? 'account' : 'login'}">${store.user ? 'Back to your account' : 'Sign in'} ${icon('arrowRight', 17)}</a>`;
  } catch (err) {
    box.innerHTML = `${formError(err.message)}
      <a class="btn btn--tonal" href="#/${store.user ? 'account' : 'login'}" style="margin-top:14px">${store.user ? 'Go to your account' : 'Sign in'}</a>`;
  }
}

// ------------------------------------------------------------------
// Privacy notice (Sri Lanka Personal Data Protection Act, No. 9 of 2022)
// ------------------------------------------------------------------
export async function renderPrivacy({ el }) {
  const section = (title, body) => `<section class="privacy__section"><h2>${title}</h2>${body}</section>`;
  el.innerHTML = `
    <article class="privacy">
      <header class="ph">
        <div class="ph__copy">
          <p class="eyebrow rule">Privacy notice</p>
          <h1 class="display ph__title">Your details, and what we do with them.</h1>
          <p class="lede ph__lede">Written for the Personal Data Protection Act, No. 9 of 2022. Plain language, no small print.</p>
        </div>
      </header>
      ${section('What we collect', `<ul>
        <li><b>To create your account:</b> your name, email address, phone number, address and date of birth.</li>
        <li><b>To let you drive:</b> your driving licence number and its expiry date. Staff compare them with your physical licence at the counter.</li>
        <li><b>To run your rentals:</b> your bookings, the payments against them, the condition of the car at pick-up and return, and any damage recorded.</li>
        <li><b>To keep the account safe:</b> sign-in attempts (email and network address) and a record of changes made to your account.</li>
      </ul>`)}
      ${section('Why', `<p>Only to rent you a car and meet the legal duties that come with it: checking that you are allowed to drive, keeping a financial record of what was paid, and settling damage or insurance claims. We do not sell your details and we do not use them for advertising.</p>`)}
      ${section('Who sees them', `<p>Staff at the branch that handles your booking, and administrators. Lists of customers show only the last four characters of your licence number; the full number is shown to staff one person at a time, and every such look is recorded.</p>`)}
      ${section('How long we keep them', `<ul>
        <li><b>Bookings and payments:</b> kept for as long as tax and accounting law requires, even after you close your account - with your name and contact details removed.</li>
        <li><b>Everything else:</b> kept while your account is open. If you do not sign in for three years we will ask whether you still want it, and erase it if you do not.</li>
        <li><b>Sign-in attempts:</b> cleared as soon as you sign in successfully.</li>
      </ul>`)}
      ${section('Your rights', `<ul>
        <li><b>A copy:</b> download everything we hold about you from your <a href="#/account">account page</a>, as a file you can keep.</li>
        <li><b>Corrections:</b> change your name, phone, address or licence details on the account page yourself. A new licence is checked again before your next booking.</li>
        <li><b>Erasure:</b> erase your account from the account page once you have no bookings open and nothing left to pay.</li>
        <li><b>Questions or complaints:</b> ask at any branch. You can also contact the Data Protection Authority of Sri Lanka.</li>
      </ul>`)}
      ${section('On this device', `<p>Your saved cars, compare list and recently viewed cars are kept in this browser so the site works before you sign in. Signed in, your saved cars are also kept on your account so they follow you to other devices.</p>`)}
    </article>
    ${footer()}`;
}
