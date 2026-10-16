#!/usr/bin/env node
/*
 * ABA PayWay DEMO SIMULATOR - for local development only. No real money moves.
 *
 *   node tools/payway-demo/payway-demo.mjs          (default port 8097, env PORT)
 *
 * It answers the same paths, field names, hash rules and JSON shapes as PayWay's
 * e-commerce checkout (https://developer.payway.com.kh), so the shop's backend runs
 * unchanged against it:
 *
 *   POST /api/payment-gateway/v1/payments/purchase              form POST -> card page (HTML)
 *   POST /api/payment-gateway/v1/payments/check-transaction-2   JSON -> payment status
 *   POST /api/payment-gateway/v1/payments/transaction-detail    JSON -> brand + last 4
 *   POST /api/payment-gateway/v1/payments/close-transaction     JSON -> cancel unpaid
 *
 * Point the backend at it with three lines (config/application.properties):
 *   app.payway.base-url=http://localhost:8097
 *   app.payway.merchant-id=demo-merchant
 *   app.payway.api-key=demo-secret-key
 *
 * Card data: the card number, expiry date and security code reach this process only to
 * pick a demo result (approved / declined) and to keep the brand and the last 4 digits.
 * They are never logged, written to disk or sent anywhere. Everything else lives in
 * memory and is gone when the process stops.
 *
 * No dependencies: node:http and node:crypto only (Node 18+).
 */
import http from 'node:http';
import crypto from 'node:crypto';

// ------------------------------------------------------------------ configuration

const PORT = Number.parseInt(process.env.PORT || '8097', 10);
/** Empty = loopback only (127.0.0.1 and ::1). Set HOST=0.0.0.0 to open it to your LAN. */
const HOST = (process.env.HOST || '').trim();
const MERCHANT_ID = process.env.PAYWAY_DEMO_MERCHANT_ID || 'demo-merchant';
const API_KEY = process.env.PAYWAY_DEMO_API_KEY || 'demo-secret-key';
const MERCHANT_NAME = process.env.PAYWAY_DEMO_MERCHANT_NAME || 'Ecomerce Shop Online';
/** PayWay documents a JSON pushback; "form" sends the same fields form-urlencoded instead. */
const CALLBACK_FORMAT =
  (process.env.PAYWAY_DEMO_CALLBACK_FORMAT || 'json').trim().toLowerCase() === 'form' ? 'form' : 'json';
const OTP_CODE = '123456';
const OTP_TRIES = 3;
const MAX_BODY = 1024 * 1024;
const MAX_TRANSACTIONS = 2000;

const PATHS = {
  purchase: '/api/payment-gateway/v1/payments/purchase',
  check: '/api/payment-gateway/v1/payments/check-transaction-2',
  detail: '/api/payment-gateway/v1/payments/transaction-detail',
  close: '/api/payment-gateway/v1/payments/close-transaction',
};

/** Purchase hash order, exactly as the docs list it (view_type / payment_gate are not in it). */
const PURCHASE_HASH_FIELDS = [
  'req_time', 'merchant_id', 'tran_id', 'amount', 'items', 'shipping',
  'firstname', 'lastname', 'email', 'phone', 'type', 'payment_option',
  'return_url', 'cancel_url', 'continue_success_url', 'return_deeplink',
  'currency', 'custom_fields', 'return_params', 'payout', 'lifetime',
  'additional_params', 'google_pay_token', 'skip_success_page',
];

/** Check Transaction: data.payment_status_code per payment_status. */
const STATUS_CODE = { APPROVED: 0, PENDING: 2, DECLINED: 3, REFUNDED: 4, CANCELLED: 7 };

/** Demo cards. Anything else is refused, so nobody is tempted to type a real card. */
const TEST_CARDS = new Map([
  ['4111111111111111', { outcome: 'approve', label: 'Visa · approved' }],
  ['5555555555554444', { outcome: 'approve', label: 'Mastercard · approved' }],
  ['4000000000000002', { outcome: 'decline', label: 'Visa · declined' }],
  ['3530111333300000', { outcome: 'approve', label: 'JCB · approved' }],
  ['6200000000000005', { outcome: 'approve', label: 'UnionPay · approved' }],
]);

const BRAND_LABEL = { visa: 'VISA', mastercard: 'Mastercard', unionpay: 'UnionPay', jcb: 'JCB' };
/** PayWay's payment_type for cards. */
const PAYMENT_TYPE = { visa: 'VISA', mastercard: 'MC', jcb: 'JCB', unionpay: 'CUP' };

// ------------------------------------------------------------------ state (memory only)

/** tran_id -> transaction */
const transactions = new Map();
/** page token -> tran_id */
const sessions = new Map();
let previewCounter = 0;

function remember(tx) {
  transactions.set(tx.tranId, tx);
  sessions.set(tx.token, tx.tranId);
  while (transactions.size > MAX_TRANSACTIONS) {
    const [oldestId, oldest] = transactions.entries().next().value;
    transactions.delete(oldestId);
    sessions.delete(oldest.token);
  }
}

// ------------------------------------------------------------------ helpers

function log(area, message) {
  const time = new Date().toTimeString().slice(0, 8);
  console.log(`[${time}] ${area.padEnd(9)} ${message}`);
}

function sign(message) {
  return crypto.createHmac('sha512', API_KEY).update(message, 'utf8').digest('base64');
}

function safeEqual(a, b) {
  const x = Buffer.from(String(a ?? ''), 'utf8');
  const y = Buffer.from(String(b ?? ''), 'utf8');
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

function purchaseHash(fields) {
  return sign(PURCHASE_HASH_FIELDS.map((name) => fields[name] ?? '').join(''));
}

/** PayWay pushback signature: values in ascending key order, arrays/objects as JSON. */
function callbackSignature(payload) {
  const b4hash = Object.keys(payload).sort()
    .map((key) => {
      const value = payload[key];
      return value !== null && typeof value === 'object' ? JSON.stringify(value) : String(value ?? '');
    })
    .join('');
  return sign(b4hash);
}

function str(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const LINE_SEPARATOR = new RegExp(String.fromCharCode(0x2028), 'g');
const PARAGRAPH_SEPARATOR = new RegExp(String.fromCharCode(0x2029), 'g');

/** JSON that is safe inside a <script> element. */
function scriptJson(value) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(LINE_SEPARATOR, '\\u2028')
    .replace(PARAGRAPH_SEPARATOR, '\\u2029');
}

function base64Decode(text) {
  if (!/^[A-Za-z0-9+/_-]+={0,2}$/.test(text)) {
    return null;
  }
  try {
    return Buffer.from(text.replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8');
  } catch {
    return null;
  }
}

function httpUrl(text) {
  if (!text) {
    return null;
  }
  try {
    const url = new URL(text);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

/** Origin + path only, so a token in a query string never reaches the log. */
function shortUrl(href) {
  try {
    const url = new URL(href);
    return url.origin + url.pathname;
  } catch {
    return '(invalid url)';
  }
}

/** "YYYY-MM-DD HH:MM:SS" in Phnom Penh time, as PayWay reports transaction_date. */
function phnomPenhTime(ms = Date.now()) {
  return new Date(ms + 7 * 3600 * 1000).toISOString().slice(0, 19).replace('T', ' ');
}

function formatMoney(amount, currency) {
  const value = Number(amount);
  if (currency === 'KHR') {
    return '៛' + Math.round(value).toLocaleString('en-US');
  }
  return '$' + value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function randomDigits(count) {
  let out = '';
  while (out.length < count) {
    out += crypto.randomInt(0, 10);
  }
  return out;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function luhn(digits) {
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let n = digits.charCodeAt(i) - 48;
    if (double) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    double = !double;
  }
  return sum % 10 === 0;
}

function brandOf(digits) {
  if (/^4/.test(digits)) return 'visa';
  if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(digits)) return 'mastercard';
  if (/^35(2[89]|[3-8])/.test(digits)) return 'jcb';
  if (/^62/.test(digits)) return 'unionpay';
  return null;
}

function isExpired(tx) {
  return Date.now() > tx.expiresAt;
}

// ------------------------------------------------------------------ request plumbing

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let tooLarge = false;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY) {
        tooLarge = true;
      } else {
        chunks.push(chunk);
      }
    });
    req.on('end', () => (tooLarge
      ? reject(new HttpError(413, 'Request body too large'))
      : resolve(Buffer.concat(chunks))));
    req.on('error', reject);
  });
}

/** Form fields (urlencoded, multipart or a flat JSON object) as a null-prototype map; null if unreadable. */
function parseFields(req, body) {
  const type = String(req.headers['content-type'] || '');
  const lower = type.toLowerCase();
  const out = Object.create(null);
  if (lower.startsWith('application/x-www-form-urlencoded')) {
    for (const [key, value] of new URLSearchParams(body.toString('utf8'))) {
      out[key] = value;
    }
    return out;
  }
  if (lower.startsWith('multipart/form-data')) {
    const match = /boundary=(?:"([^"]+)"|([^;\s]+))/i.exec(type);
    const boundary = match && (match[1] || match[2]);
    if (!boundary) {
      return null;
    }
    for (const part of body.toString('latin1').split('--' + boundary)) {
      const split = part.indexOf('\r\n\r\n');
      if (split < 0) continue;
      const head = part.slice(0, split);
      let value = part.slice(split + 4);
      if (value.endsWith('\r\n')) value = value.slice(0, -2);
      const name = /content-disposition:[^\r\n]*?\bname="([^"]*)"/i.exec(head);
      if (!name || /\bfilename=/i.test(head)) continue;
      out[name[1]] = Buffer.from(value, 'latin1').toString('utf8');
    }
    return out;
  }
  if (lower.startsWith('application/json')) {
    let parsed;
    try {
      parsed = JSON.parse(body.toString('utf8'));
    } catch {
      return null;
    }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return null;
    }
    for (const [key, value] of Object.entries(parsed)) {
      out[key] = value === null || value === undefined ? '' : (typeof value === 'object' ? JSON.stringify(value) : String(value));
    }
    return out;
  }
  return null;
}

function readJson(body) {
  try {
    const parsed = JSON.parse(body.toString('utf8') || '{}');
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function sendJson(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
  });
  res.end(body);
}

function sendHtml(res, status, html, nonce) {
  const csp = [
    "default-src 'none'",
    "style-src 'unsafe-inline'",
    nonce ? `script-src 'nonce-${nonce}'` : "script-src 'none'",
    "img-src data:",
    "connect-src 'self'",
    "base-uri 'none'",
    "form-action 'self'",
  ].join('; ');
  res.writeHead(status, {
    'Content-Type': 'text/html; charset=utf-8',
    'Content-Length': Buffer.byteLength(html),
    'Cache-Control': 'no-store',
    'Content-Security-Policy': csp,
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
  });
  res.end(html);
}

function wantsHtml(req) {
  return /text\/html/i.test(String(req.headers.accept || ''));
}

/**
 * PayWay answers a refused purchase with {"status":{"code":..,"message":..}}. In a browser
 * (popup iframe or hosted tab) that JSON is wrapped in a readable page with a hint.
 */
function purchaseError(req, res, code, message, hint, tranId) {
  const payload = { status: { code, message, ...(tranId ? { tran_id: tranId } : {}) } };
  log('purchase', `REFUSED${tranId ? ` tran ${tranId}` : ''}: ${code} ${message}${hint ? ` - ${hint}` : ''}`);
  if (!wantsHtml(req)) {
    sendJson(res, 400, payload);
    return;
  }
  sendHtml(res, 400, errorPage(code, message, hint, payload));
}

// ------------------------------------------------------------------ purchase

async function handlePurchase(req, res) {
  const fields = parseFields(req, await readBody(req));
  if (!fields) {
    purchaseError(req, res, 400, 'Unsupported content type',
      'Post the purchase as a form (application/x-www-form-urlencoded or multipart/form-data).');
    return;
  }
  const f = (name) => str(fields[name]);
  const tranId = f('tran_id');

  if (!f('merchant_id') || f('merchant_id') !== MERCHANT_ID) {
    purchaseError(req, res, 8, 'Invalid merchant profile',
      `app.payway.merchant-id must be "${MERCHANT_ID}" for this simulator.`, tranId);
    return;
  }
  if (!f('hash') || !safeEqual(f('hash'), purchaseHash(fields))) {
    const posted = PURCHASE_HASH_FIELDS.filter((name) => (fields[name] ?? '') !== '');
    purchaseError(req, res, 1, 'Wrong hash',
      `app.payway.api-key must equal the simulator's key, and the hash must cover, in the documented order: ${posted.join(', ')}.`,
      tranId);
    return;
  }
  if (!f('req_time')) {
    purchaseError(req, res, 1, 'Wrong hash', 'req_time is required (UTC, yyyyMMddHHmmss).', tranId);
    return;
  }
  warnOnClockDrift(f('req_time'), tranId);
  if (!/^[A-Za-z0-9_-]{1,20}$/.test(tranId)) {
    purchaseError(req, res, 2, 'Invalid transaction ID', 'tran_id: 1-20 letters or digits.', tranId);
    return;
  }

  const currency = (f('currency') || 'USD').toUpperCase();
  if (currency !== 'USD' && currency !== 'KHR') {
    purchaseError(req, res, 12, 'Currency type is not allowed', 'currency: USD or KHR.', tranId);
    return;
  }
  const amount = f('amount');
  if (!/^\d+(\.\d+)?$/.test(amount)) {
    purchaseError(req, res, 3, 'Invalid amount', 'amount: a plain number such as 12.50.', tranId);
    return;
  }
  if (Number(amount) <= 0) {
    purchaseError(req, res, 45, 'Amount cannot be zero', null, tranId);
    return;
  }
  if (currency === 'KHR' && amount.includes('.')) {
    purchaseError(req, res, 46, 'KHR amount cannot have decimals', null, tranId);
    return;
  }
  if (currency === 'KHR' && Number(amount) <= 100) {
    purchaseError(req, res, 47, 'KHR amount must be greater than 100', null, tranId);
    return;
  }
  if (currency === 'USD' && /\.\d{3,}$/.test(amount)) {
    purchaseError(req, res, 3, 'Invalid amount', 'USD amounts have at most 2 decimals.', tranId);
    return;
  }

  const paymentOption = f('payment_option');
  if (paymentOption && paymentOption !== 'cards') {
    purchaseError(req, res, 23, 'Payment option is not enabled',
      'This simulator only provides payment_option=cards.', tranId);
    return;
  }
  let lifetime = 30 * 24 * 60;   // PayWay's default: 30 days
  if (f('lifetime')) {
    lifetime = Number.parseInt(f('lifetime'), 10);
    if (!/^\d+$/.test(f('lifetime')) || lifetime < 3) {
      purchaseError(req, res, 69, 'Payment lifetime must be at least 3 minutes', null, tranId);
      return;
    }
  }
  let items = [];
  if (f('items')) {
    const decoded = base64Decode(f('items'));
    try {
      items = JSON.parse(decoded ?? 'x');
    } catch {
      items = null;
    }
    if (!Array.isArray(items) || items.length > 50) {
      purchaseError(req, res, 13, 'Invalid items', 'items: Base64 of a JSON array of {name, quantity, price}.', tranId);
      return;
    }
  }
  if (f('email') && (f('email').length > 50 || !f('email').includes('@'))) {
    purchaseError(req, res, 19, 'Invalid email', null, tranId);
    return;
  }
  if (f('phone').length > 20) {
    purchaseError(req, res, 18, 'Invalid phone number', null, tranId);
    return;
  }
  for (const [name, code] of [['firstname', 16], ['lastname', 17]]) {
    if (f(name) && (f(name).length > 100 || /[\d<>{}[\]\\/@#$%^*=+|~]/.test(f(name)))) {
      purchaseError(req, res, code, `Invalid ${name === 'firstname' ? 'first' : 'last'} name`, null, tranId);
      return;
    }
  }
  let returnUrl = null;
  if (f('return_url')) {
    returnUrl = httpUrl(base64Decode(f('return_url')));
    if (!returnUrl) {
      purchaseError(req, res, 81, 'Return URL is invalid or not whitelisted',
        'return_url must be the Base64 of an http(s) URL.', tranId);
      return;
    }
  }
  const successUrl = httpUrl(f('continue_success_url'));
  const cancelUrl = httpUrl(f('cancel_url'));
  for (const [name, value, parsed] of [['continue_success_url', f('continue_success_url'), successUrl],
    ['cancel_url', f('cancel_url'), cancelUrl]]) {
    if (value && !parsed) {
      log('purchase', `tran ${tranId}: ${name} is not an http(s) URL - ignored`);
    }
  }

  const existing = transactions.get(tranId);
  if (existing) {
    const reopenable = (existing.status === 'PENDING' || existing.status === 'DECLINED')
      && existing.amount === amount && existing.currency === currency && !existing.preview;
    if (!reopenable) {
      purchaseError(req, res, 4, 'Duplicated transaction ID',
        `tran ${tranId} is already ${existing.status}; every attempt needs a new tran_id.`, tranId);
      return;
    }
    log('purchase', `tran ${tranId} re-opened (${existing.status})`);
    sendCardPage(res, existing, 'form');
    return;
  }

  const now = Date.now();
  const tx = {
    tranId,
    merchantId: MERCHANT_ID,
    amount,
    currency,
    items: items.map((item) => ({
      name: String(item?.name ?? '').slice(0, 100),
      quantity: Number(item?.quantity) || 0,
      price: Number(item?.price) || 0,
    })),
    email: f('email'),
    firstname: f('firstname'),
    lastname: f('lastname'),
    phone: f('phone'),
    returnParams: f('return_params'),
    returnUrl,
    successUrl,
    cancelUrl,
    viewType: f('view_type') || '(not sent)',
    createdAt: now,
    expiresAt: now + lifetime * 60 * 1000,
    status: 'PENDING',
    apv: '',
    bankRef: '',
    brand: null,
    last4: null,
    transactionDate: phnomPenhTime(now),
    token: crypto.randomBytes(24).toString('base64url'),
    pending: null,
    preview: false,
  };
  remember(tx);
  log('purchase', `tran ${tranId}: ${amount} ${currency}, view_type ${tx.viewType},`
    + ` callback ${returnUrl ? shortUrl(returnUrl) : 'none'} -> card page`);
  sendCardPage(res, tx, 'form');
}

function warnOnClockDrift(reqTime, tranId) {
  const m = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(reqTime);
  if (!m) {
    log('purchase', `tran ${tranId}: req_time "${reqTime.slice(0, 20)}" is not yyyyMMddHHmmss`);
    return;
  }
  const at = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6]);
  const driftMinutes = Math.abs(Date.now() - at) / 60000;
  if (driftMinutes > 15) {
    log('purchase', `tran ${tranId}: req_time is ${Math.round(driftMinutes)} min away from now - it must be UTC`);
  }
}

// ------------------------------------------------------------------ check / detail / close

async function readTransactionRequest(req, res) {
  const body = parseFields(req, await readBody(req));
  if (!body) {
    sendJson(res, 400, { status: { code: '400', message: 'Send JSON {req_time, merchant_id, tran_id, hash}' } });
    return null;
  }
  const reqTime = str(body.req_time);
  const merchantId = str(body.merchant_id);
  const tranId = str(body.tran_id);
  if (merchantId !== MERCHANT_ID) {
    sendJson(res, 200, { status: { code: '8', message: 'Invalid merchant profile', tran_id: tranId } });
    return null;
  }
  if (!str(body.hash) || !safeEqual(str(body.hash), sign(reqTime + merchantId + tranId))) {
    log('check', `tran ${tranId || '-'}: wrong hash (code 5)`);
    sendJson(res, 200, { status: { code: '5', message: 'Invalid hash', tran_id: tranId } });
    return null;
  }
  const tx = transactions.get(tranId);
  if (!tx) {
    sendJson(res, 200, { status: { code: 6, message: 'Transaction not found', tran_id: tranId } });
    return null;
  }
  return tx;
}

function transactionData(tx) {
  const amount = Number(tx.amount);
  return {
    payment_status_code: STATUS_CODE[tx.status],
    total_amount: amount,
    original_amount: amount,
    refund_amount: 0,
    discount_amount: 0,
    payment_amount: amount,
    payment_currency: tx.currency,
    original_currency: tx.currency,
    apv: tx.apv,
    payment_status: tx.status,
    transaction_date: tx.transactionDate,
  };
}

function maskedAccount(tx) {
  return tx.last4 ? `****${tx.last4}` : '';
}

async function handleCheck(req, res) {
  const tx = await readTransactionRequest(req, res);
  if (!tx) return;
  log('check', `tran ${tx.tranId} -> ${tx.status}`);
  sendJson(res, 200, {
    data: transactionData(tx),
    status: { code: '00', message: 'Success!', tran_id: tx.tranId },
  });
}

async function handleDetail(req, res) {
  const tx = await readTransactionRequest(req, res);
  if (!tx) return;
  log('detail', `tran ${tx.tranId} -> ${tx.status}${tx.brand ? ` ${BRAND_LABEL[tx.brand]} ****${tx.last4}` : ''}`);
  sendJson(res, 200, {
    data: {
      ...transactionData(tx),
      first_name: tx.firstname,
      last_name: tx.lastname,
      email: tx.email,
      phone: tx.phone,
      bank_ref: tx.bankRef,
      payment_type: tx.brand ? PAYMENT_TYPE[tx.brand] : '',
      payer_account: maskedAccount(tx),
      bank_name: '',
      card_source: tx.brand ? 'OFFUS_INTERNATIONAL' : '',
      transaction_operations: tx.status === 'PENDING' ? [] : [{
        status: tx.status,
        amount: Number(tx.amount),
        transaction_date: tx.transactionDate,
        bank_ref: tx.bankRef,
      }],
    },
    status: { code: '00', message: 'Success!', tran_id: tx.tranId },
  });
}

async function handleClose(req, res) {
  const tx = await readTransactionRequest(req, res);
  if (!tx) return;
  if (tx.status === 'APPROVED') {
    sendJson(res, 200, { status: { code: '3', message: 'Transaction is already approved', tran_id: tx.tranId } });
    return;
  }
  if (tx.status !== 'CANCELLED') {
    tx.status = 'CANCELLED';
    tx.pending = null;
    tx.transactionDate = phnomPenhTime();
    log('close', `tran ${tx.tranId} closed by the merchant -> CANCELLED`);
  }
  sendJson(res, 200, { status: { code: '00', message: 'Success!', tran_id: tx.tranId } });
}

// ------------------------------------------------------------------ pushback (callback)

function callbackPayload(tx) {
  const amount = Number(tx.amount);
  return {
    tran_id: tx.tranId,
    apv: tx.apv,
    status: String(STATUS_CODE[tx.status]),
    return_params: tx.returnParams,
    original_amount: amount,
    original_currency: tx.currency,
    payment_amount: amount,
    payment_currency: tx.currency,
    total_amount: amount,
    discount_amount: 0,
    transaction_date: tx.transactionDate,
    first_name: tx.firstname,
    last_name: tx.lastname,
    email: tx.email,
    phone: tx.phone,
    bank_ref: tx.bankRef,
    payment_type: tx.brand ? PAYMENT_TYPE[tx.brand] : '',
    payer_account: maskedAccount(tx),
    bank_name: '',
    card_source: tx.brand ? 'OFFUS_INTERNATIONAL' : '',
  };
}

async function sendCallback(tx) {
  if (!tx.returnUrl) {
    log('callback', `tran ${tx.tranId}: no return_url - nothing pushed (the shop confirms by polling)`);
    return;
  }
  const payload = callbackPayload(tx);
  const body = CALLBACK_FORMAT === 'form'
    ? new URLSearchParams(Object.entries(payload).map(([k, v]) => [k, String(v ?? '')])).toString()
    : JSON.stringify(payload);
  try {
    const response = await fetch(tx.returnUrl, {
      method: 'POST',
      headers: {
        'Content-Type': CALLBACK_FORMAT === 'form' ? 'application/x-www-form-urlencoded' : 'application/json',
        'X-PayWay-HMAC-SHA512': callbackSignature(payload),
        'User-Agent': 'payway-demo-simulator',
      },
      body,
      redirect: 'manual',
      signal: AbortSignal.timeout(10_000),
    });
    await response.arrayBuffer().catch(() => null);
    log('callback', `POST ${shortUrl(tx.returnUrl)} (${CALLBACK_FORMAT}, tran ${tx.tranId}, status ${payload.status})`
      + ` -> HTTP ${response.status}`);
  } catch (error) {
    log('callback', `POST ${shortUrl(tx.returnUrl)} (tran ${tx.tranId}) failed: ${error?.cause?.code || error?.name || 'error'}`);
  }
}

// ------------------------------------------------------------------ card page API (/__demo/*)

async function handleSession(req, res, token, action) {
  const body = readJson(await readBody(req));
  const tranId = sessions.get(token);
  const tx = tranId && transactions.get(tranId);
  if (!tx || tx.token !== token) {
    sendJson(res, 404, { error: 'unknown_session' });
    return;
  }
  if (tx.status === 'APPROVED') {
    sendJson(res, 409, { error: 'already_paid' });
    return;
  }
  if (action === 'cancel') {
    if (tx.status !== 'CANCELLED') {
      tx.status = 'CANCELLED';
      tx.pending = null;
      tx.transactionDate = phnomPenhTime();
      log('card', `tran ${tx.tranId}: shopper cancelled -> CANCELLED`);
    }
    sendJson(res, 200, { redirect: tx.cancelUrl });
    return;
  }
  if (tx.status === 'CANCELLED') {
    sendJson(res, 409, { error: 'cancelled' });
    return;
  }
  if (isExpired(tx)) {
    sendJson(res, 409, { error: 'expired' });
    return;
  }
  if (action === 'card') {
    await submitCard(res, tx, body);
  } else {
    await submitOtp(res, tx, body);
  }
}

/** Validates the card, keeps only brand + last 4 + the demo outcome. Nothing is logged. */
async function submitCard(res, tx, body) {
  const number = String(body.number ?? '').replace(/\D/g, '');
  const month = Number.parseInt(String(body.exp_month ?? ''), 10);
  const year = Number.parseInt(String(body.exp_year ?? ''), 10);
  const cvc = String(body.cvc ?? '');
  const errors = {};
  const brand = brandOf(number);
  if (number.length < 12 || number.length > 19) {
    errors.number = 'Your card number is incomplete.';
  } else if (!luhn(number)) {
    errors.number = 'Your card number is invalid.';
  } else if (!brand) {
    errors.number = 'We accept Visa, Mastercard, UnionPay and JCB.';
  } else if (!TEST_CARDS.has(number)) {
    errors.number = 'Demo mode: please use one of the test cards listed below.';
  }
  const now = new Date();
  const fullYear = year < 100 ? 2000 + year : year;
  if (!(month >= 1 && month <= 12) || !Number.isFinite(fullYear)) {
    errors.exp = "Your card's expiration date is incomplete.";
  } else if (fullYear * 12 + month < now.getFullYear() * 12 + now.getMonth() + 1) {
    errors.exp = "Your card's expiration date is in the past.";
  }
  if (!/^\d{3,4}$/.test(cvc)) {
    errors.cvc = "Your card's security code is incomplete.";
  }
  await sleep(450);
  if (Object.keys(errors).length) {
    sendJson(res, 422, { error: 'invalid', fields: errors });
    return;
  }
  tx.pending = { brand, last4: number.slice(-4), outcome: TEST_CARDS.get(number).outcome, tries: 0 };
  log('card', `tran ${tx.tranId}: ${BRAND_LABEL[brand]} ****${tx.pending.last4} entered -> 3-D Secure`);
  sendJson(res, 200, { step: 'otp', brand: BRAND_LABEL[brand], last4: tx.pending.last4 });
}

async function submitOtp(res, tx, body) {
  if (!tx.pending) {
    sendJson(res, 409, { error: 'no_card' });
    return;
  }
  const otp = String(body.otp ?? '').replace(/\D/g, '');
  await sleep(600);
  if (otp !== OTP_CODE) {
    tx.pending.tries += 1;
    if (tx.pending.tries >= OTP_TRIES) {
      tx.pending = null;
      log('card', `tran ${tx.tranId}: 3-D Secure failed ${OTP_TRIES} times`);
      sendJson(res, 422, { error: 'otp_locked' });
      return;
    }
    sendJson(res, 422, { error: 'otp', attemptsLeft: OTP_TRIES - tx.pending.tries });
    return;
  }
  const { brand, last4, outcome } = tx.pending;
  tx.pending = null;
  tx.brand = brand;
  tx.last4 = last4;
  tx.transactionDate = phnomPenhTime();
  tx.bankRef = 'DEMO' + randomDigits(12);
  if (outcome === 'approve') {
    tx.status = 'APPROVED';
    tx.apv = randomDigits(6);
    log('card', `tran ${tx.tranId}: APPROVED ${tx.amount} ${tx.currency} (${BRAND_LABEL[brand]} ****${last4}, apv ${tx.apv})`);
  } else {
    tx.status = 'DECLINED';
    tx.apv = '';
    log('card', `tran ${tx.tranId}: DECLINED (${BRAND_LABEL[brand]} ****${last4})`);
  }
  if (!tx.preview) {
    sendCallback(tx);   // not awaited: the shopper is not kept waiting on the shop's server
  }
  sendJson(res, 200, outcome === 'approve'
    ? { result: 'approved', redirect: tx.successUrl }
    : { result: 'declined' });
}

// ------------------------------------------------------------------ preview (GET, for screenshots)

function handlePreview(req, res, url) {
  const amount = /^\d+(\.\d{1,2})?$/.test(url.searchParams.get('amount') || '')
    ? Number(url.searchParams.get('amount')).toFixed(2) : '24.90';
  const step = url.searchParams.get('step') || 'form';
  const now = Date.now();
  previewCounter += 1;
  const tx = {
    tranId: `PREVIEW${previewCounter}`,
    merchantId: MERCHANT_ID,
    amount,
    currency: 'USD',
    items: [
      { name: 'Cotton T-shirt (M, Navy)', quantity: 1, price: 14.9 },
      { name: 'Canvas tote bag', quantity: 1, price: 10 },
    ],
    email: '', firstname: '', lastname: '', phone: '',
    returnParams: 'ORD-PREVIEW',
    returnUrl: null,
    successUrl: '/__preview/done',
    cancelUrl: '/__preview/done?cancelled=1',
    viewType: 'preview',
    createdAt: now,
    expiresAt: step === 'expired' ? now - 1 : now + 30 * 60 * 1000,
    status: 'PENDING', apv: '', bankRef: '', brand: null, last4: null,
    transactionDate: phnomPenhTime(now),
    token: crypto.randomBytes(24).toString('base64url'),
    pending: null,
    preview: true,
  };
  remember(tx);
  sendCardPage(res, tx, ['form', 'otp', 'approved', 'declined', 'cancelled', 'expired'].includes(step) ? step : 'form');
}

function handlePreviewDone(res, url) {
  const cancelled = url.searchParams.has('cancelled');
  sendHtml(res, 200, simplePage('Preview finished',
    `<h1>${cancelled ? 'Cancelled' : 'Payment approved'}</h1>
     <p>In the shop, the browser would now be on <code>${cancelled ? 'cancel_url' : 'continue_success_url'}</code>.</p>
     <p><a href="/__preview">Open the card page again</a></p>`));
}

// ------------------------------------------------------------------ HTML

const MARKS = {
  visa: `<svg class="mark" data-brand="visa" viewBox="0 0 34 22" width="34" height="22"><rect width="34" height="22" rx="3.5" fill="#1434CB"/><text x="17" y="15.2" text-anchor="middle" font-family="Arial,Helvetica,sans-serif" font-size="10.5" font-weight="900" font-style="italic" letter-spacing=".2" fill="#fff">VISA</text></svg>`,
  mastercard: `<svg class="mark" data-brand="mastercard" viewBox="0 0 34 22" width="34" height="22"><rect width="34" height="22" rx="3.5" class="mark-plate"/><circle cx="13.5" cy="11" r="6.5" fill="#EB001B"/><circle cx="20.5" cy="11" r="6.5" fill="#F79E1B"/><path d="M17 5.52A6.5 6.5 0 0 1 17 16.48 6.5 6.5 0 0 1 17 5.52Z" fill="#FF5F00"/></svg>`,
  unionpay: `<svg class="mark" data-brand="unionpay" viewBox="0 0 34 22" width="34" height="22"><rect width="34" height="22" rx="3.5" class="mark-plate"/><path d="M7.6 2.5h6.6c.9 0 1.4.7 1.2 1.6l-2.9 13.8c-.2.9-1 1.6-1.9 1.6H4c-.9 0-1.4-.7-1.2-1.6L5.7 4.1c.2-.9 1-1.6 1.9-1.6Z" fill="#E21836"/><path d="M15.2 2.5h7.2c.9 0 1.4.7 1.2 1.6l-2.9 13.8c-.2.9-1 1.6-1.9 1.6h-7.2c-.9 0-1.4-.7-1.2-1.6l2.9-13.8c.2-.9 1-1.6 1.9-1.6Z" fill="#00447C"/><path d="M23.1 2.5h6.2c.9 0 1.4.7 1.2 1.6l-2.9 13.8c-.2.9-1 1.6-1.9 1.6h-6.2c-.9 0-1.4-.7-1.2-1.6l2.9-13.8c.2-.9 1-1.6 1.9-1.6Z" fill="#007B84"/><text x="17.4" y="12.6" text-anchor="middle" font-family="Arial,Helvetica,sans-serif" font-size="5.6" font-weight="700" font-style="italic" fill="#fff">UnionPay</text></svg>`,
  jcb: `<svg class="mark" data-brand="jcb" viewBox="0 0 34 22" width="34" height="22"><rect x=".5" y=".5" width="33" height="21" rx="3" fill="#fff" stroke="#dcdce2"/><rect x="5" y="3.5" width="7.6" height="15" rx="2.4" fill="#0E4C96"/><rect x="13.2" y="3.5" width="7.6" height="15" rx="2.4" fill="#E21836"/><rect x="21.4" y="3.5" width="7.6" height="15" rx="2.4" fill="#007940"/><g font-family="Arial,Helvetica,sans-serif" font-size="7.2" font-weight="700" fill="#fff" text-anchor="middle"><text x="8.8" y="13.6">J</text><text x="17" y="13.6">C</text><text x="25.2" y="13.6">B</text></g></svg>`,
};

// Theme colours go through style="" (var() in SVG presentation attributes is not reliable).
const ICONS = {
  cardBack: `<svg viewBox="0 0 32 22" width="30" height="21" fill="none"><rect x="1" y="1.5" width="25" height="17" rx="2.5" stroke="currentColor" stroke-width="1.4"/><rect x="1.7" y="5" width="23.6" height="3.4" fill="currentColor"/><rect x="4.5" y="11.5" width="9" height="2.2" rx="1" fill="currentColor" opacity=".45"/><rect x="17" y="12.2" width="15" height="9.3" rx="2" style="fill:var(--field)"/><text x="24.5" y="19.6" text-anchor="middle" font-family="Arial,Helvetica,sans-serif" font-size="7.2" font-weight="700" fill="currentColor">123</text></svg>`,
  lock: `<svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true"><rect x="3" y="7" width="10" height="7.5" rx="1.6" fill="currentColor"/><path d="M5.2 7V5.2a2.8 2.8 0 0 1 5.6 0V7" stroke="currentColor" stroke-width="1.5"/></svg>`,
  warn: `<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true"><path d="M8 1.5 15 14H1L8 1.5Z" fill="currentColor"/><path d="M8 6v3.6" style="stroke:var(--rib-a)" stroke-width="1.6" stroke-linecap="round"/><circle cx="8" cy="11.6" r=".9" style="fill:var(--rib-a)"/></svg>`,
  shield: `<svg viewBox="0 0 48 48" width="44" height="44" fill="none" aria-hidden="true"><path d="M24 4 40 10v12c0 10.5-6.8 18.6-16 22-9.2-3.4-16-11.5-16-22V10L24 4Z" style="fill:var(--accent-soft);stroke:var(--accent)" stroke-width="2"/><rect x="17" y="21" width="14" height="10.5" rx="2" style="fill:var(--accent)"/><path d="M20 21v-2.6a4 4 0 0 1 8 0V21" style="stroke:var(--accent)" stroke-width="2"/></svg>`,
  ok: `<svg viewBox="0 0 48 48" width="52" height="52" aria-hidden="true"><circle cx="24" cy="24" r="22" style="fill:var(--ok-soft)"/><path d="m15 24.5 6 6 12-13" fill="none" style="stroke:var(--ok)" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
  fail: `<svg viewBox="0 0 48 48" width="52" height="52" aria-hidden="true"><circle cx="24" cy="24" r="22" style="fill:var(--danger-soft)"/><path d="m17 17 14 14M31 17 17 31" fill="none" style="stroke:var(--danger)" stroke-width="3.4" stroke-linecap="round"/></svg>`,
  clock: `<svg viewBox="0 0 48 48" width="52" height="52" aria-hidden="true"><circle cx="24" cy="24" r="22" style="fill:var(--soft)"/><circle cx="24" cy="24" r="12" fill="none" style="stroke:var(--muted)" stroke-width="3"/><path d="M24 17v7.5l5 3" fill="none" style="stroke:var(--muted)" stroke-width="3" stroke-linecap="round"/></svg>`,
};

const CSS = `
:root{
  color-scheme:light;
  --bg:#ffffff;--fg:#1b1b1f;--muted:#686873;--label:#1b1b1f;
  --field:#ffffff;--line:#d4d4db;--line-hover:#b5b5c0;--ph:#9797a2;
  --accent:#1660d6;--accent-hover:#0f50b8;--on-accent:#ffffff;--ring:rgba(22,96,214,.25);--accent-soft:#e8f0fd;
  --danger:#c4271f;--danger-soft:#fdeceb;--ok:#18794e;--ok-soft:#e5f4ec;
  --soft:#f5f5f7;--soft-line:#e3e3e9;--divider:#ececf1;
  --rib-a:#ffe7a3;--rib-b:#ffdd80;--rib-fg:#5a3a00;--rib-line:#ecc45a;
  --plate:transparent;--tip-bg:#1b1b1f;--tip-fg:#ffffff;
}
@media (prefers-color-scheme:dark){:root{
  color-scheme:dark;
  --bg:#171717;--fg:#efefef;--muted:#a3a3ab;--label:#f2f2f2;
  --field:#1f1f1f;--line:#393939;--line-hover:#4c4c4c;--ph:#7b7b83;
  --accent:#3b82f6;--accent-hover:#5b97f8;--on-accent:#ffffff;--ring:rgba(59,130,246,.35);--accent-soft:#16233a;
  --danger:#ff7b72;--danger-soft:#3a1c1a;--ok:#4ade80;--ok-soft:#123020;
  --soft:#1e1e1e;--soft-line:#323232;--divider:#2a2a2a;
  --rib-a:#3a2e00;--rib-b:#463800;--rib-fg:#ffd866;--rib-line:#5f4b00;
  --tip-bg:#f2f2f2;--tip-fg:#171717;
}}
*{box-sizing:border-box}
html,body{margin:0;background:var(--bg);color:var(--fg)}
body{font:15px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,"Noto Sans Khmer",sans-serif;-webkit-font-smoothing:antialiased;-webkit-text-size-adjust:100%}
[hidden]{display:none!important}
.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
.ribbon{display:flex;align-items:center;justify-content:center;flex-wrap:wrap;gap:6px 8px;padding:7px 16px;
  background:repeating-linear-gradient(-45deg,var(--rib-a) 0 12px,var(--rib-b) 12px 24px);color:var(--rib-fg);
  border-bottom:1px solid var(--rib-line);font-size:12.5px;font-weight:700;letter-spacing:.05em;text-transform:uppercase;text-align:center}
.ribbon .sub{font-weight:600;letter-spacing:.02em;text-transform:none;opacity:.85}
.wrap{max-width:560px;margin:0 auto;padding:20px 24px 22px}
.merchant{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.eyebrow{font-size:12.5px;color:var(--muted);font-weight:500}
.mname{font-size:15px;font-weight:600;margin-top:2px;overflow-wrap:anywhere}
.ref{font-size:12.5px;color:var(--muted);margin-top:2px;overflow-wrap:anywhere}
.amount{text-align:right;flex:none}
.amt{font-size:26px;font-weight:700;letter-spacing:-.01em;line-height:1.15;margin-top:2px;font-variant-numeric:tabular-nums}
details.items{margin-top:10px;font-size:13.5px;color:var(--muted)}
details.items summary{cursor:pointer;width:max-content;max-width:100%;border-radius:4px}
details.items summary:focus-visible{outline:2px solid var(--accent);outline-offset:2px}
details.items ul{list-style:none;margin:8px 0 0;padding:10px 12px;border:1px solid var(--soft-line);border-radius:8px;background:var(--soft)}
details.items li{display:flex;justify-content:space-between;gap:12px;padding:2px 0;color:var(--fg)}
details.items li span:last-child{flex:none;font-variant-numeric:tabular-nums}
.divider{height:1px;background:var(--divider);margin:16px 0 4px;border:0}
.title{font-size:17px;font-weight:650;margin:18px 0 14px;letter-spacing:-.005em}
.field{margin-bottom:16px;min-width:0}
.lbl{display:block;font-weight:600;font-size:15px;margin-bottom:7px;color:var(--label)}
.ctl{position:relative}
.input{display:block;width:100%;height:42px;padding:0 12px;border:1px solid var(--line);border-radius:8px;background:var(--field);color:var(--fg);
  font:inherit;font-size:16px;letter-spacing:.01em;transition:border-color .15s,box-shadow .15s}
.input::placeholder{color:var(--ph);opacity:1}
.input:hover{border-color:var(--line-hover)}
.input:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px var(--ring)}
.input[aria-invalid="true"]{border-color:var(--danger)}
.input[aria-invalid="true"]:focus{box-shadow:0 0 0 3px color-mix(in srgb,var(--danger) 25%,transparent)}
.input:disabled{opacity:.65}
.input.num{padding-right:164px;font-variant-numeric:tabular-nums}
.input.csc{padding-right:50px}
.brands{position:absolute;right:9px;top:50%;transform:translateY(-50%);display:flex;gap:4px;pointer-events:none}
.mark{display:block;width:34px;height:22px;transition:opacity .15s,filter .15s,transform .15s}
.mark-plate{fill:var(--plate)}
.brands.has .mark:not(.on){opacity:.22;filter:grayscale(1)}
.brands.has .mark.on{transform:scale(1.06)}
.csc-icon{position:absolute;right:10px;top:50%;transform:translateY(-50%);color:var(--muted);display:flex;pointer-events:none}
.row{display:grid;grid-template-columns:1fr 1fr;gap:20px}
.err{color:var(--danger);font-size:13.5px;margin-top:6px}
.err:empty{display:none}
.alert{display:flex;gap:8px;align-items:flex-start;padding:10px 12px;border-radius:8px;background:var(--danger-soft);color:var(--danger);font-size:14px;margin:-2px 0 14px}
.save{display:flex;align-items:center;gap:8px;margin-top:2px}
.check{display:inline-flex;align-items:center;gap:10px;cursor:pointer;font-size:15px}
.check input{appearance:none;-webkit-appearance:none;flex:none;width:18px;height:18px;margin:0;border:1px solid var(--line);border-radius:4px;background:var(--field);
  display:grid;place-content:center;cursor:pointer;transition:background .15s,border-color .15s}
.check input::before{content:"";width:10px;height:6px;border-left:2px solid var(--on-accent);border-bottom:2px solid var(--on-accent);transform:translateY(-1px) rotate(-45deg);opacity:0}
.check input:checked{background:var(--accent);border-color:var(--accent)}
.check input:checked::before{opacity:1}
.check input:focus-visible{outline:none;box-shadow:0 0 0 3px var(--ring);border-color:var(--accent)}
.tip{position:relative;display:inline-flex}
.tip-btn{width:18px;height:18px;border-radius:50%;border:1px solid var(--line);background:transparent;color:var(--muted);font:700 11px/1 Georgia,serif;font-style:italic;
  display:grid;place-content:center;cursor:help;padding:0}
.tip-btn:focus-visible{outline:none;box-shadow:0 0 0 3px var(--ring);border-color:var(--accent)}
.bubble{position:absolute;bottom:calc(100% + 8px);right:-8px;width:max-content;max-width:240px;padding:8px 10px;border-radius:8px;background:var(--tip-bg);color:var(--tip-fg);
  font-size:12.5px;line-height:1.4;box-shadow:0 6px 20px rgba(0,0,0,.18);opacity:0;visibility:hidden;transition:opacity .12s;z-index:5}
.tip:hover .bubble,.tip:focus-within .bubble{opacity:1;visibility:visible}
.pay{width:100%;height:46px;margin-top:20px;border:0;border-radius:8px;background:var(--accent);color:var(--on-accent);font:inherit;font-size:16px;font-weight:650;
  cursor:pointer;display:flex;align-items:center;justify-content:center;gap:10px;transition:background .15s}
.pay:hover{background:var(--accent-hover)}
.pay:focus-visible{outline:none;box-shadow:0 0 0 3px var(--bg),0 0 0 5px var(--accent)}
.pay:disabled{cursor:progress;opacity:.8}
.spinner{display:none;width:16px;height:16px;border-radius:50%;border:2px solid currentColor;border-right-color:transparent;animation:spin .7s linear infinite}
.busy .spinner{display:block}
@keyframes spin{to{transform:rotate(360deg)}}
.link{display:block;margin:12px auto 0;padding:6px 8px;border:0;background:none;color:var(--muted);font:inherit;font-size:14px;text-decoration:underline;text-underline-offset:3px;cursor:pointer;border-radius:6px}
.link:hover{color:var(--fg)}
.link:focus-visible{outline:2px solid var(--accent);outline-offset:1px}
.note{margin-top:18px;padding:12px 14px;border:1px dashed var(--soft-line);border-radius:10px;background:var(--soft);font-size:13px;color:var(--muted)}
.note-title{font-weight:650;color:var(--fg);font-size:13px;margin-bottom:6px}
.note ul{list-style:none;margin:0;padding:0;display:grid;gap:2px}
.note li{display:grid;grid-template-columns:auto 1fr;align-items:center;gap:0 10px}
.tc{border:0;background:none;padding:2px 4px;margin-left:-4px;border-radius:4px;color:var(--accent);font:600 13px ui-monospace,SFMono-Regular,Consolas,Menlo,monospace;cursor:pointer;font-variant-numeric:tabular-nums}
.tc:hover{text-decoration:underline}
.tc:focus-visible{outline:2px solid var(--accent)}
.note p{margin:6px 0 0}
.note b{color:var(--fg)}
.foot{margin:16px 0 0;font-size:12.5px;color:var(--muted);text-align:center}
.foot svg{display:inline-block;vertical-align:-2px;margin-right:6px}
.panel{text-align:center;padding:22px 4px 6px}
.panel h2{font-size:20px;margin:12px 0 6px;letter-spacing:-.01em}
.panel p{margin:0 auto 6px;max-width:400px;color:var(--muted)}
.panel .pay{max-width:320px;margin:18px auto 0}
.chip{display:inline-flex;align-items:center;gap:8px;margin:8px 0 2px;padding:6px 10px;border-radius:999px;background:var(--soft);border:1px solid var(--soft-line);font-size:13.5px;font-weight:600;color:var(--fg)}
.otp-field{max-width:260px;margin:18px auto 0;text-align:left}
.otp{text-align:center;font-size:22px;letter-spacing:.4em;height:48px;font-variant-numeric:tabular-nums;padding-left:calc(12px + .4em)}
.hint{display:inline-block;margin-top:10px;padding:4px 10px;border-radius:6px;background:var(--rib-a);color:var(--rib-fg);font-size:13px;font-weight:600}
@media (max-width:440px){
  .wrap{padding:16px 16px 18px}
  .amt{font-size:22px}
  .input.num{padding-right:136px}
  .brands{gap:3px;right:8px}
  .mark{width:29px;height:19px}
  .brands.has .mark:not(.on){display:none}
  .row{gap:12px}
}
@media (max-width:360px){
  .input.num{padding-right:112px;font-size:15px}
  .mark{width:24px;height:16px}
  .brands{gap:2px}
  .tc{font-size:12px}
}
@media (max-width:300px){.row{grid-template-columns:1fr;gap:0}}
@media (prefers-reduced-motion:reduce){*{transition:none!important;animation-duration:1.5s!important}}
`;

function ribbon() {
  return `<div class="ribbon" role="note">${ICONS.warn}<span>DEMO — no real money</span><span class="sub">PayWay simulator · test mode</span></div>`;
}

function simplePage(title, inner) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark"><meta name="robots" content="noindex"><title>${escapeHtml(title)}</title>
<style>${CSS}
.doc h1{font-size:20px;margin:6px 0 10px}.doc h2{font-size:15px;margin:18px 0 6px}.doc p,.doc li{color:var(--muted)}
.doc pre,.doc code{font:13px ui-monospace,SFMono-Regular,Consolas,Menlo,monospace}.doc pre{background:var(--soft);border:1px solid var(--soft-line);border-radius:8px;padding:10px 12px;overflow:auto;color:var(--fg);white-space:pre-wrap;word-break:break-all}
.doc a{color:var(--accent)}</style></head><body>${ribbon()}<main class="wrap doc">${inner}</main></body></html>`;
}

function errorPage(code, message, hint, payload) {
  return simplePage(`PayWay error ${code}`, `
  <div class="panel" style="padding-top:8px">${ICONS.fail}<h2>Payment page could not open</h2>
  <p>PayWay refused the request: <b style="color:var(--fg)">${escapeHtml(message)}</b> (code ${escapeHtml(code)}).</p></div>
  ${hint ? `<h2>How to fix</h2><p>${escapeHtml(hint)}</p>` : ''}
  <h2>PayWay's answer</h2><pre>${escapeHtml(JSON.stringify(payload))}</pre>
  <p>This page comes from the local PayWay demo simulator. The details are also in its console.</p>`);
}

function sendCardPage(res, tx, view) {
  const nonce = crypto.randomBytes(16).toString('base64');
  const amountLabel = formatMoney(tx.amount, tx.currency);
  const itemCount = tx.items.reduce((sum, item) => sum + (item.quantity || 0), 0);
  const items = tx.items.length ? `
    <details class="items"><summary>Order details · ${itemCount} item${itemCount === 1 ? '' : 's'}</summary><ul>
    ${tx.items.map((item) => `<li><span>${escapeHtml(item.name)}${item.quantity > 1 ? ` × ${item.quantity}` : ''}</span><span>${escapeHtml(formatMoney(item.price * (item.quantity || 1), tx.currency))}</span></li>`).join('')}
    </ul></details>` : '';
  const reference = [tx.returnParams && `Order ${tx.returnParams.slice(0, 40)}`, `Ref ${tx.tranId}`].filter(Boolean).join(' · ');
  const testCards = [...TEST_CARDS.entries()].map(([number, card]) =>
    `<li><button type="button" class="tc" data-fill="${number}" aria-label="Use test card ${number.replace(/(\d{4})(?=\d)/g, '$1 ')}">${number.replace(/(\d{4})(?=\d)/g, '$1 ')}</button><span>${escapeHtml(card.label)}</span></li>`).join('');
  const boot = {
    token: tx.token,
    view,
    amountLabel,
    preview: tx.preview,
    sample: { brand: 'VISA', last4: '1111' },
  };

  const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="robots" content="noindex">
<title>Pay ${escapeHtml(amountLabel)} by card · PayWay demo</title>
<style>${CSS}</style>
</head>
<body>
${ribbon()}
<main class="wrap">
  <header class="merchant">
    <div>
      <div class="eyebrow">Pay to</div>
      <div class="mname">${escapeHtml(MERCHANT_NAME)}</div>
      <div class="ref">${escapeHtml(reference)}</div>
    </div>
    <div class="amount">
      <div class="eyebrow">Amount</div>
      <div class="amt">${escapeHtml(amountLabel)}</div>
    </div>
  </header>
  ${items}
  <hr class="divider">

  <section id="view-form" aria-labelledby="pm-title"${view === 'form' ? '' : ' hidden'}>
    <form id="card-form" novalidate autocomplete="on">
      <h1 class="title" id="pm-title" tabindex="-1">Payment method</h1>
      <div id="form-alert" class="alert" role="alert" hidden></div>
      <div class="field">
        <label class="lbl" for="cc-number">Card number</label>
        <div class="ctl">
          <input id="cc-number" class="input num" type="text" inputmode="numeric" autocomplete="cc-number" placeholder="1234 1234 1234 1234"
            maxlength="23" spellcheck="false" autocorrect="off" aria-describedby="cc-number-err cc-number-hint" aria-invalid="false">
          <div class="brands" id="brands" aria-hidden="true">${MARKS.visa}${MARKS.mastercard}${MARKS.unionpay}${MARKS.jcb}</div>
        </div>
        <span class="sr-only" id="cc-number-hint">Visa, Mastercard, UnionPay and JCB, including virtual cards. <span id="brand-live" aria-live="polite"></span></span>
        <div class="err" id="cc-number-err"></div>
      </div>
      <div class="row">
        <div class="field">
          <label class="lbl" for="cc-exp">Expiration date</label>
          <input id="cc-exp" class="input" type="text" inputmode="numeric" autocomplete="cc-exp" placeholder="MM / YY"
            maxlength="7" spellcheck="false" aria-describedby="cc-exp-err" aria-invalid="false">
          <div class="err" id="cc-exp-err"></div>
        </div>
        <div class="field">
          <label class="lbl" for="cc-csc">Security code</label>
          <div class="ctl">
            <input id="cc-csc" class="input csc" type="text" inputmode="numeric" autocomplete="cc-csc" placeholder="CVC"
              maxlength="4" spellcheck="false" aria-describedby="cc-csc-err cc-csc-hint" aria-invalid="false">
            <span class="csc-icon" aria-hidden="true">${ICONS.cardBack}</span>
          </div>
          <span class="sr-only" id="cc-csc-hint">The 3 or 4 digits on the back of your card.</span>
          <div class="err" id="cc-csc-err"></div>
        </div>
      </div>
      <div class="save">
        <label class="check" title="Demo only: nothing is saved"><input type="checkbox" id="cc-save" aria-describedby="save-tip"><span>Save payment details for future purchases</span></label>
        <span class="tip"><button type="button" class="tip-btn" aria-label="About saving payment details">i</button><span class="bubble" role="tooltip" id="save-tip">Demo only: nothing is saved. This simulator never stores card details.</span></span>
      </div>
      <button class="pay" id="pay" type="submit"><span class="spinner" aria-hidden="true"></span><span id="pay-label">Pay ${escapeHtml(amountLabel)}</span></button>
      <button type="button" class="link" data-cancel>Cancel and return to the shop</button>
    </form>
    <aside class="note" aria-labelledby="tc-title">
      <div class="note-title" id="tc-title">Demo test cards</div>
      <ul>${testCards}</ul>
      <p>Any future expiry date and any 3-digit CVC. 3-D Secure code: <b>${OTP_CODE}</b>. Other numbers are refused.</p>
    </aside>
  </section>

  <section id="view-otp" class="panel" aria-labelledby="otp-title"${view === 'otp' ? '' : ' hidden'}>
    ${ICONS.shield}
    <h2 id="otp-title" tabindex="-1">Verify it's you</h2>
    <p>3-D Secure check by your card issuer (simulated). Enter the 6-digit code sent to the phone number linked to your card.</p>
    <div class="chip"><span id="otp-card">VISA •••• 1111</span><span aria-hidden="true">·</span><span>${escapeHtml(amountLabel)}</span></div>
    <form id="otp-form" novalidate>
      <div class="otp-field">
        <label class="lbl" for="otp-input">One-time code</label>
        <input id="otp-input" class="input otp" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" placeholder="••••••"
          spellcheck="false" aria-describedby="otp-err otp-hint" aria-invalid="false">
        <div class="err" id="otp-err" role="alert"></div>
      </div>
      <span class="hint" id="otp-hint">Demo code: ${OTP_CODE}</span>
      <button class="pay" id="otp-submit" type="submit"><span class="spinner" aria-hidden="true"></span><span>Verify and pay</span></button>
      <button type="button" class="link" id="otp-back">Use a different card</button>
    </form>
  </section>

  <section id="view-approved" class="panel" aria-labelledby="ok-title"${view === 'approved' ? '' : ' hidden'}>
    ${ICONS.ok}
    <h2 id="ok-title" tabindex="-1">Payment approved</h2>
    <p>${escapeHtml(amountLabel)} paid to ${escapeHtml(MERCHANT_NAME)}.</p>
    <p id="ok-next" role="status">Returning to the shop…</p>
  </section>

  <section id="view-declined" class="panel" aria-labelledby="no-title"${view === 'declined' ? '' : ' hidden'}>
    ${ICONS.fail}
    <h2 id="no-title" tabindex="-1">Declined — try another card</h2>
    <p>The card issuer declined this payment. No money was taken.</p>
    <button type="button" class="pay" id="retry">Try another card</button>
    <button type="button" class="link" data-cancel>Cancel and return to the shop</button>
  </section>

  <section id="view-cancelled" class="panel" aria-labelledby="cx-title"${view === 'cancelled' ? '' : ' hidden'}>
    ${ICONS.clock}
    <h2 id="cx-title" tabindex="-1">Payment cancelled</h2>
    <p>Nothing was charged. You can close this window and pay again from the shop.</p>
  </section>

  <section id="view-expired" class="panel" aria-labelledby="ex-title"${view === 'expired' ? '' : ' hidden'}>
    ${ICONS.clock}
    <h2 id="ex-title" tabindex="-1">This payment link has expired</h2>
    <p>Nothing was charged. Go back to the shop and start the card payment again.</p>
    <button type="button" class="link" data-cancel>Return to the shop</button>
  </section>

  <section id="view-closed" class="panel" aria-labelledby="cl-title" hidden>
    ${ICONS.ok}
    <h2 id="cl-title" tabindex="-1">Already paid</h2>
    <p>This payment was completed earlier. You can close this window.</p>
  </section>

  <p class="foot">${ICONS.lock}<span>Simulated ABA PayWay checkout · card details are never stored or logged</span></p>
</main>
<script nonce="${nonce}">(${clientMain.toString()})(${scriptJson(boot)});</script>
</body>
</html>`;
  sendHtml(res, 200, html, nonce);
}

/**
 * Runs in the shopper's browser (serialized into the card page). Self-contained: it must not
 * use anything from this module's scope.
 */
function clientMain(boot) {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const VIEWS = ['form', 'otp', 'approved', 'declined', 'cancelled', 'expired', 'closed'];
  const NAMES = { visa: 'Visa', mastercard: 'Mastercard', unionpay: 'UnionPay', jcb: 'JCB' };
  const number = $('cc-number');
  const exp = $('cc-exp');
  const cvc = $('cc-csc');
  const brands = $('brands');
  const marks = Array.from(brands.querySelectorAll('.mark'));
  const payBtn = $('pay');
  const otpInput = $('otp-input');
  const otpBtn = $('otp-submit');
  const touched = { number: false, exp: false, cvc: false };
  let busy = false;

  function show(view) {
    VIEWS.forEach((name) => { $('view-' + name).hidden = name !== view; });
    window.scrollTo(0, 0);
    const heading = document.querySelector('#view-' + view + ' [tabindex="-1"]');
    if (view === 'otp') {
      otpInput.value = '';
      setError(otpInput, $('otp-err'), '');
      otpInput.focus();
    } else if (heading) {
      heading.focus({ preventScroll: true });
    }
  }

  // ---------------------------------------------------------------- formatting
  function detect(d) {
    if (/^4/.test(d)) return 'visa';
    if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(d)) return 'mastercard';
    if (/^35(2[89]|[3-8])/.test(d)) return 'jcb';
    if (/^62/.test(d)) return 'unionpay';
    return null;
  }
  function maxDigits(brand) { return brand === 'unionpay' ? 19 : 16; }
  function luhn(d) {
    let sum = 0;
    let dbl = false;
    for (let i = d.length - 1; i >= 0; i--) {
      let n = d.charCodeAt(i) - 48;
      if (dbl) { n *= 2; if (n > 9) n -= 9; }
      sum += n;
      dbl = !dbl;
    }
    return sum % 10 === 0;
  }
  function groupNumber(d) { return d.replace(/(\d{4})(?=\d)/g, '$1 '); }
  function groupExp(d, adding) {
    if (d.length > 2) return d.slice(0, 2) + ' / ' + d.slice(2, 4);
    if (d.length === 2 && adding) return d + ' / ';
    return d;
  }
  function digitsOf(el) { return el.value.replace(/\D/g, ''); }

  /** Writes a re-formatted value and keeps the caret after the same number of digits. */
  function rewrite(el, next, digitsBefore, wasAtEnd) {
    if (el.value === next) return;
    el.value = next;
    if (document.activeElement !== el) return;
    let pos = next.length;
    if (!wasAtEnd) {
      pos = 0;
      let seen = 0;
      while (pos < next.length && seen < digitsBefore) {
        if (next[pos] >= '0' && next[pos] <= '9') seen++;
        pos++;
      }
    }
    el.setSelectionRange(pos, pos);
  }
  function caretInfo(el) {
    const caret = el.selectionStart == null ? el.value.length : el.selectionStart;
    return { before: el.value.slice(0, caret).replace(/\D/g, '').length, atEnd: caret >= el.value.length };
  }

  function setBrand(brand) {
    brands.classList.toggle('has', !!brand);
    marks.forEach((mark) => mark.classList.toggle('on', mark.getAttribute('data-brand') === brand));
    const live = $('brand-live');
    const text = brand ? NAMES[brand] + ' card' : '';
    if (live.textContent !== text) live.textContent = text;
  }

  // ---------------------------------------------------------------- validation
  function numberError() {
    const d = digitsOf(number);
    const brand = detect(d);
    if (!d) return 'Enter your card number.';
    if (d.length < (brand === 'unionpay' ? 16 : brand ? 16 : 12)) return 'Your card number is incomplete.';
    if (!luhn(d)) return 'Your card number is invalid.';
    if (!brand) return 'We accept Visa, Mastercard, UnionPay and JCB.';
    return '';
  }
  function expParts() {
    const d = digitsOf(exp);
    return { month: parseInt(d.slice(0, 2), 10), year: parseInt(d.slice(2, 4), 10), complete: d.length === 4 };
  }
  function expError() {
    const d = digitsOf(exp);
    if (!d) return 'Enter the expiration date.';
    const p = expParts();
    if (!(p.month >= 1 && p.month <= 12)) return 'Enter a valid month (01–12).';
    if (!p.complete) return "Your card's expiration date is incomplete.";
    const now = new Date();
    if ((2000 + p.year) * 12 + p.month < now.getFullYear() * 12 + now.getMonth() + 1) {
      return "Your card's expiration date is in the past.";
    }
    return '';
  }
  function cvcError() {
    const d = digitsOf(cvc);
    if (!d) return 'Enter the security code.';
    if (d.length < 3) return "Your card's security code is incomplete.";
    return '';
  }
  function setError(input, box, message) {
    box.textContent = message;
    input.setAttribute('aria-invalid', message ? 'true' : 'false');
  }
  const checks = {
    number: [number, $('cc-number-err'), numberError],
    exp: [exp, $('cc-exp-err'), expError],
    cvc: [cvc, $('cc-csc-err'), cvcError],
  };
  function validate(name, force) {
    const [input, box, rule] = checks[name];
    const message = rule();
    if (force || touched[name] || !message) setError(input, box, message);
    return message;
  }

  // ---------------------------------------------------------------- inputs
  number.addEventListener('input', (event) => {
    const info = caretInfo(number);
    let d = digitsOf(number);
    const brand = detect(d);
    d = d.slice(0, maxDigits(brand));
    rewrite(number, groupNumber(d), info.before, info.atEnd);
    setBrand(brand);
    validate('number');
    const typing = event.inputType && event.inputType.indexOf('insert') === 0;
    const full = d.length === maxDigits(brand) || (brand !== 'unionpay' && d.length === 16);
    if (typing && info.atEnd && full && !numberError()) exp.focus();
  });
  exp.addEventListener('input', (event) => {
    const info = caretInfo(exp);
    const adding = !event.inputType || event.inputType.indexOf('insert') === 0;
    let d = digitsOf(exp).slice(0, 4);
    if (d.length === 1 && d > '1') d = '0' + d;
    rewrite(exp, groupExp(d, adding && info.atEnd), info.before, info.atEnd);
    validate('exp');
    if (adding && info.atEnd && d.length === 4 && !expError()) cvc.focus();
  });
  cvc.addEventListener('input', () => {
    const d = digitsOf(cvc).slice(0, 4);
    if (cvc.value !== d) cvc.value = d;
    validate('cvc');
  });
  [['number', number], ['exp', exp], ['cvc', cvc]].forEach(([name, input]) => {
    input.addEventListener('blur', () => {
      if (input.value) touched[name] = true;
      if (touched[name]) validate(name, true);
    });
  });
  otpInput.addEventListener('input', () => {
    const d = otpInput.value.replace(/\D/g, '').slice(0, 6);
    if (otpInput.value !== d) otpInput.value = d;
    if (d.length === 6) setError(otpInput, $('otp-err'), '');
  });

  function alertBox(message) {
    const box = $('form-alert');
    box.textContent = message || '';
    box.hidden = !message;
  }
  function setBusy(button, on) {
    busy = on;
    button.disabled = on;
    button.classList.toggle('busy', on);
    button.setAttribute('aria-busy', on ? 'true' : 'false');
    [number, exp, cvc, $('cc-save')].forEach((el) => { el.disabled = on; });
  }

  async function api(action, body) {
    try {
      const response = await fetch('/__demo/session/' + encodeURIComponent(boot.token) + '/' + action, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body || {}),
        credentials: 'omit',
        cache: 'no-store',
      });
      return await response.json();
    } catch (error) {
      return { error: 'network' };
    }
  }

  function handleCommon(res) {
    if (res.error === 'already_paid') { show('closed'); return true; }
    if (res.error === 'cancelled') { show('cancelled'); return true; }
    if (res.error === 'expired') { show('expired'); return true; }
    if (res.error === 'unknown_session') {
      show('form');
      alertBox('This payment page is no longer valid. Go back to the shop and start again.');
      return true;
    }
    if (res.error === 'network') {
      alertBox('Could not reach the payment server. Check your connection and try again.');
      return true;
    }
    return false;
  }

  // ---------------------------------------------------------------- submit card
  $('card-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    alertBox('');
    touched.number = touched.exp = touched.cvc = true;
    const first = ['number', 'exp', 'cvc'].find((name) => validate(name, true));
    if (first) { checks[first][0].focus(); return; }
    const p = expParts();
    setBusy(payBtn, true);
    const res = await api('card', {
      number: digitsOf(number),
      exp_month: p.month,
      exp_year: p.year,
      cvc: digitsOf(cvc),
    });
    setBusy(payBtn, false);
    if (res.step === 'otp') {
      $('otp-card').textContent = res.brand + ' •••• ' + res.last4;
      show('otp');
      return;
    }
    if (res.error === 'invalid' && res.fields) {
      let focus = null;
      ['number', 'exp', 'cvc'].forEach((name) => {
        if (res.fields[name]) {
          setError(checks[name][0], checks[name][1], res.fields[name]);
          focus = focus || checks[name][0];
        }
      });
      if (focus) focus.focus();
      return;
    }
    if (!handleCommon(res)) alertBox('Something went wrong. Please try again.');
  });

  // ---------------------------------------------------------------- 3-D Secure
  $('otp-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    const code = otpInput.value.replace(/\D/g, '');
    if (code.length !== 6) {
      setError(otpInput, $('otp-err'), 'Enter the 6-digit code.');
      otpInput.focus();
      return;
    }
    setBusy(otpBtn, true);
    otpInput.disabled = true;
    const res = await api('otp', { otp: code });
    setBusy(otpBtn, false);
    otpInput.disabled = false;
    if (res.result === 'approved') {
      show('approved');
      if (res.redirect) {
        setTimeout(() => { window.location.replace(res.redirect); }, 1400);
      } else {
        $('ok-next').textContent = 'You can close this window now.';
      }
      return;
    }
    if (res.result === 'declined') { show('declined'); return; }
    if (res.error === 'otp') {
      setError(otpInput, $('otp-err'), 'That code is not right. ' + res.attemptsLeft
        + (res.attemptsLeft === 1 ? ' try' : ' tries') + ' left.');
      otpInput.select();
      return;
    }
    if (res.error === 'otp_locked' || res.error === 'no_card') {
      show('form');
      alertBox(res.error === 'otp_locked'
        ? 'Too many wrong codes. Enter your card details again.'
        : 'Enter your card details first.');
      return;
    }
    if (handleCommon(res)) {
      if (res.error === 'network') {
        setError(otpInput, $('otp-err'), 'Could not reach the payment server. Try again.');
      }
      return;
    }
    setError(otpInput, $('otp-err'), 'Something went wrong. Please try again.');
  });
  $('otp-back').addEventListener('click', () => { show('form'); number.focus(); });

  // ---------------------------------------------------------------- declined / cancel
  $('retry').addEventListener('click', () => {
    number.value = '';
    cvc.value = '';
    setBrand(null);
    touched.number = touched.cvc = false;
    setError(number, $('cc-number-err'), '');
    setError(cvc, $('cc-csc-err'), '');
    alertBox('');
    show('form');
    number.focus();
  });
  document.querySelectorAll('[data-cancel]').forEach((button) => {
    button.addEventListener('click', async () => {
      if (busy) return;
      busy = true;
      const res = await api('cancel');
      busy = false;
      if (res.redirect) { window.location.replace(res.redirect); return; }
      if (!handleCommon(res)) show('cancelled');
    });
  });

  // ---------------------------------------------------------------- demo test cards
  document.querySelectorAll('[data-fill]').forEach((button) => {
    button.addEventListener('click', () => {
      const d = button.getAttribute('data-fill');
      const year = (new Date().getFullYear() + 3) % 100;
      number.value = groupNumber(d);
      exp.value = '12 / ' + String(year).padStart(2, '0');
      cvc.value = '123';
      setBrand(detect(d));
      ['number', 'exp', 'cvc'].forEach((name) => validate(name, true));
      alertBox('');
      payBtn.focus();
    });
  });

  // ---------------------------------------------------------------- start
  if (boot.view === 'otp') {
    $('otp-card').textContent = boot.sample.brand + ' •••• ' + boot.sample.last4;
  }
  if (boot.view === 'approved' && boot.preview) {
    $('ok-next').textContent = 'Returning to the shop…';
  }
  if (boot.view === 'otp') {
    otpInput.focus();
  }
}

// ------------------------------------------------------------------ index page

function handleIndex(res) {
  const base = `http://localhost:${PORT}`;
  const keyLine = process.env.PAYWAY_DEMO_API_KEY ? '&lt;the value of PAYWAY_DEMO_API_KEY&gt;' : escapeHtml(API_KEY);
  const counts = { PENDING: 0, APPROVED: 0, DECLINED: 0, CANCELLED: 0 };
  for (const tx of transactions.values()) {
    if (!tx.preview) counts[tx.status] = (counts[tx.status] || 0) + 1;
  }
  sendHtml(res, 200, simplePage('PayWay demo simulator', `
  <h1>PayWay demo simulator is running</h1>
  <p>Merchant <b style="color:var(--fg)">${escapeHtml(MERCHANT_ID)}</b> · callback format ${CALLBACK_FORMAT} ·
     transactions: ${counts.PENDING} pending, ${counts.APPROVED} approved, ${counts.DECLINED} declined, ${counts.CANCELLED} cancelled.</p>
  <h2>Backend configuration (config/application.properties)</h2>
  <pre>app.payway.base-url=${escapeHtml(base)}
app.payway.merchant-id=${escapeHtml(MERCHANT_ID)}
app.payway.api-key=${keyLine}</pre>
  <h2>Endpoints</h2>
  <pre>POST ${PATHS.purchase}
POST ${PATHS.check}
POST ${PATHS.detail}
POST ${PATHS.close}</pre>
  <p><a href="/__preview">Preview the card page</a> (sample order, no shop needed).</p>`));
}

// ------------------------------------------------------------------ router

async function route(req, res) {
  const url = new URL(req.url || '/', 'http://localhost');
  const path = url.pathname.replace(/\/{2,}/g, '/');
  const isPost = req.method === 'POST';
  const isGet = req.method === 'GET' || req.method === 'HEAD';

  const api = {
    [PATHS.purchase]: handlePurchase,
    [PATHS.check]: handleCheck,
    [PATHS.detail]: handleDetail,
    [PATHS.close]: handleClose,
  }[path];
  if (api) {
    if (!isPost) {
      res.setHeader('Allow', 'POST');
      sendJson(res, 405, { status: { code: 405, message: 'Method Not Allowed' } });
      return;
    }
    await api(req, res);
    return;
  }
  const session = /^\/__demo\/session\/([A-Za-z0-9_-]{16,64})\/(card|otp|cancel)$/.exec(path);
  if (session) {
    if (!isPost) {
      sendJson(res, 405, { error: 'method_not_allowed' });
      return;
    }
    if (!String(req.headers['content-type'] || '').toLowerCase().startsWith('application/json')) {
      sendJson(res, 415, { error: 'json_required' });
      return;
    }
    await handleSession(req, res, session[1], session[2]);
    return;
  }
  if (isGet && path === '/__preview') return handlePreview(req, res, url);
  if (isGet && path === '/__preview/done') return handlePreviewDone(res, url);
  if (isGet && path === '/') return handleIndex(res);
  if (path === '/favicon.ico') {
    res.writeHead(204);
    res.end();
    return;
  }
  sendJson(res, 404, { status: { code: 404, message: 'Not found' } });
}

async function onRequest(req, res) {
  try {
    await route(req, res);
  } catch (error) {
    const status = error instanceof HttpError ? error.status : 500;
    if (status === 500) {
      // The message only - a request body (which may hold card data) is never printed.
      log('error', `${req.method} ${(req.url || '').split('?')[0]}: ${error?.message || error}`);
    }
    if (!res.headersSent) {
      sendJson(res, status, { status: { code: status === 500 ? 11 : status, message: status === 500 ? 'Internal error' : error.message } });
    } else {
      res.end();
    }
  }
}

// ------------------------------------------------------------------ start

function listen(host) {
  return new Promise((resolve) => {
    const server = http.createServer(onRequest);
    server.on('error', (error) => resolve({ host, error }));
    server.listen(PORT, host, () => resolve({ host, server }));
  });
}

const hosts = HOST ? [HOST] : ['127.0.0.1', '::1'];
const results = await Promise.all(hosts.map(listen));
const servers = results.filter((r) => r.server).map((r) => r.server);
for (const r of results) {
  if (r.error && !(r.host === '::1' && servers.length)) {
    console.error(r.error.code === 'EADDRINUSE'
      ? `Port ${PORT} is already in use on ${r.host} - is the simulator already running? Use PORT=<other> to pick another port.`
      : `Could not listen on ${r.host}:${PORT}: ${r.error.message}`);
  }
}
if (!servers.length) {
  process.exit(1);
}
console.log('');
console.log('  ABA PayWay DEMO simulator  -  no real money');
console.log(`  http://localhost:${PORT}            status + config`);
console.log(`  http://localhost:${PORT}/__preview  card page with a sample order`);
console.log(`  merchant ${MERCHANT_ID}, api key ${process.env.PAYWAY_DEMO_API_KEY ? '(from PAYWAY_DEMO_API_KEY)' : API_KEY}, callback ${CALLBACK_FORMAT}`);
console.log(`  test cards: 4111 1111 1111 1111 approved, 5555 5555 5555 4444 approved, 4000 0000 0000 0002 declined; OTP ${OTP_CODE}`);
console.log('');

function shutdown() {
  servers.forEach((server) => server.close());
  setTimeout(() => process.exit(0), 300).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
