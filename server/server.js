// Simple Messenger server. No dependencies: run with `node server.js`.
// Stores users and messages in data.json next to this file.
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const os = require('os');

const PORT = Number(process.env.PORT) || 3000;
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
const MAX_MESSAGES = 5000;
const ONLINE_MS = 40000;

let db = { users: [], messages: [], seq: 0 };
try { db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch { /* first run */ }

let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    fs.writeFile(DATA_FILE, JSON.stringify(db), (err) => { if (err) console.error('Could not save:', err.message); });
  }, 200);
}

const lastSeen = new Map(); // userId -> time
const waiting = new Set(); // open long-poll requests

const publicUser = (u) => ({ id: u.id, name: u.name, online: Date.now() - (lastSeen.get(u.id) || 0) < ONLINE_MS });
const visibleTo = (m, userId) => m.to === 'all' || m.to === userId || m.from === userId;

function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(data) });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.setEncoding('utf8');
    req.on('data', (c) => {
      raw += c;
      if (raw.length > 100000) { reject(new Error('Too large')); req.destroy(); }
    });
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch { reject(new Error('Invalid JSON')); }
    });
  });
}

function authUser(req) {
  const m = /^Bearer (.+)$/.exec(req.headers.authorization || '');
  const user = m && db.users.find((u) => u.token === m[1]);
  if (user) lastSeen.set(user.id, Date.now());
  return user;
}

function update(user, since) {
  const messages = db.messages.filter((m) => m.seq > since && visibleTo(m, user.id));
  return { messages, users: db.users.map(publicUser), last: db.seq };
}

function wakeWaiters(message) {
  for (const w of [...waiting]) {
    if (visibleTo(message, w.user.id)) {
      waiting.delete(w);
      clearTimeout(w.timer);
      send(w.res, 200, update(w.user, w.since));
    }
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  try {
    if (req.method === 'GET' && url.pathname === '/') {
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end('Simple Messenger server is running.\n');
      return;
    }

    // Signs in, or creates the account if the name is new.
    if (req.method === 'POST' && url.pathname === '/api/join') {
      const { name, password } = await readBody(req);
      const clean = String(name || '').trim().replace(/\s+/g, ' ');
      const pass = String(password || '');
      if (clean.length < 2 || clean.length > 30) return send(res, 400, { error: 'Name must be 2 to 30 characters' });
      if (pass.length < 4) return send(res, 400, { error: 'Password must be at least 4 characters' });
      const existing = db.users.find((u) => u.name.toLowerCase() === clean.toLowerCase());
      if (existing) {
        const hash = crypto.scryptSync(pass, existing.salt, 32);
        if (!crypto.timingSafeEqual(hash, Buffer.from(existing.hash, 'hex'))) {
          return send(res, 403, { error: 'Wrong password for that name' });
        }
        lastSeen.set(existing.id, Date.now());
        return send(res, 200, { id: existing.id, name: existing.name, token: existing.token });
      }
      const salt = crypto.randomBytes(16).toString('hex');
      const user = {
        id: crypto.randomUUID(), name: clean, token: crypto.randomBytes(24).toString('hex'),
        salt, hash: crypto.scryptSync(pass, salt, 32).toString('hex'), created: Date.now(),
      };
      db.users.push(user);
      save();
      lastSeen.set(user.id, Date.now());
      console.log(`New account: ${clean}`);
      return send(res, 200, { id: user.id, name: user.name, token: user.token });
    }

    const user = authUser(req);
    if (!user) return send(res, 401, { error: 'Please join again' });

    if (req.method === 'GET' && url.pathname === '/api/users') {
      return send(res, 200, { users: db.users.map(publicUser) });
    }

    if (req.method === 'POST' && url.pathname === '/api/messages') {
      const { to, text } = await readBody(req);
      const body = String(text || '').trim();
      if (!body || body.length > 4000) return send(res, 400, { error: 'Message must be 1 to 4000 characters' });
      if (to !== 'all' && !db.users.some((u) => u.id === to)) return send(res, 404, { error: 'That person does not exist' });
      const message = { id: crypto.randomUUID(), seq: ++db.seq, from: user.id, fromName: user.name, to, text: body, ts: Date.now() };
      db.messages.push(message);
      if (db.messages.length > MAX_MESSAGES) db.messages.splice(0, db.messages.length - MAX_MESSAGES);
      save();
      send(res, 200, message);
      wakeWaiters(message);
      return;
    }

    // Long poll: answers at once if there is something new, otherwise waits up to `wait` seconds.
    if (req.method === 'GET' && url.pathname === '/api/updates') {
      const since = Number(url.searchParams.get('since')) || 0;
      const wait = Math.min(Number(url.searchParams.get('wait')) || 0, 30);
      const now = update(user, since);
      if (now.messages.length || !wait) return send(res, 200, now);
      const w = { res, user, since };
      w.timer = setTimeout(() => { waiting.delete(w); send(res, 200, update(user, since)); }, wait * 1000);
      waiting.add(w);
      req.on('close', () => { waiting.delete(w); clearTimeout(w.timer); });
      return;
    }

    send(res, 404, { error: 'Not found' });
  } catch (e) {
    if (!res.headersSent) send(res, 400, { error: e.message });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Simple Messenger server running on port ${PORT}`);
  console.log('In the app, enter one of these server addresses:');
  for (const list of Object.values(os.networkInterfaces())) {
    for (const a of list || []) if (a.family === 'IPv4' && !a.internal) console.log(`  http://${a.address}:${PORT}`);
  }
});
