process.env.NODE_ENV = 'test';

const { test, before, after, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const { createApp } = require('../src/app');

let server;
let baseUrl;
let dbRef;
let stmtsRef;
let sentEmails = [];

function extractCookie(res) {
  const setCookie = res.headers.getSetCookie ? res.headers.getSetCookie() : [];
  assert.ok(setCookie.length > 0, 'Un cookie httpOnly doit être retourné');
  const raw = setCookie[0];
  assert.match(raw, /HttpOnly/i, 'Le cookie JWT doit contenir l\'attribut HttpOnly');
  return raw.split(';')[0];
}

function extractTokenFromEmail(emailObj) {
  const match = emailObj.text.match(/token=([a-f0-9]+)/i);
  return match ? match[1] : null;
}

before(async () => {
  const instance = createApp({
    dbPath: ':memory:',
    jwtSecret: 'test-secret-lubumarket-256bit',
    appBaseUrl: 'http://localhost:4000',
    customEmailSender: async (mail) => {
      sentEmails.push(mail);
      return { accepted: [mail.to] };
    }
  });
  dbRef = instance.db;
  stmtsRef = instance.stmts;

  await new Promise((resolve) => {
    server = instance.app.listen(0, '127.0.0.1', () => {
      const addr = server.address();
      baseUrl = `http://127.0.0.1:${addr.port}`;
      resolve();
    });
  });
});

after(async () => {
  if (server) {
    await new Promise((resolve) => server.close(resolve));
  }
  if (dbRef) {
    dbRef.close();
  }
});

beforeEach(() => {
  sentEmails = [];
  dbRef.exec('DELETE FROM users');
});

test('Inscription : valide format email, 8+ caractères, confirmation, unicité et hachage bcrypt', async () => {
  // Mot de passe trop court refusé
  const shortRes = await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'client@lubum.cd', password: '123', confirmPassword: '123' })
  });
  assert.equal(shortRes.status, 400);

  // Inscription valide
  const okRes = await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'client@lubum.cd',
      password: 'Password123!',
      confirmPassword: 'Password123!'
    })
  });
  assert.equal(okRes.status, 201);
  const okBody = await okRes.json();
  assert.equal(okBody.requiresEmailVerification, true);

  // Vérifie le hachage bcrypt dans SQLite (jamais en clair)
  const stored = stmtsRef.findByEmail.get('client@lubum.cd');
  assert.ok(stored.password_hash.startsWith('$2b$'));
  assert.notEqual(stored.password_hash, 'Password123!');

  // Email déjà utilisé refusé (409)
  const dupRes = await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'client@lubum.cd',
      password: 'Password123!',
      confirmPassword: 'Password123!'
    })
  });
  assert.equal(dupRes.status, 409);

  // Email de confirmation envoyé
  assert.equal(sentEmails.length, 1);
  assert.equal(sentEmails[0].to, 'client@lubum.cd');
});

test('Vérification email obligatoire, erreur générique de connexion, cookie httpOnly JWT et route protégée', async () => {
  await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'marie@lubum.cd',
      password: 'SecretPass88',
      confirmPassword: 'SecretPass88'
    })
  });

  // Connexion bloquée tant que l'email n'est pas vérifié
  const unverifiedRes = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'marie@lubum.cd', password: 'SecretPass88' })
  });
  assert.equal(unverifiedRes.status, 403);

  // Erreur générique sur email inconnu et mauvais mot de passe
  const unknownRes = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'inconnu@lubum.cd', password: 'SecretPass88' })
  });
  assert.equal(unknownRes.status, 401);
  assert.equal((await unknownRes.json()).message, 'Email ou mot de passe incorrect');

  // Confirmation via lien reçu par email
  const verifyToken = extractTokenFromEmail(sentEmails[0]);
  assert.ok(verifyToken);
  const verifyRes = await fetch(`${baseUrl}/api/auth/verify-email?token=${verifyToken}`, {
    headers: { Accept: 'application/json' }
  });
  assert.equal(verifyRes.status, 200);

  // Mauvais mot de passe sur compte vérifié => même erreur générique
  const wrongPwRes = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'marie@lubum.cd', password: 'WrongPassword99' })
  });
  assert.equal(wrongPwRes.status, 401);
  assert.equal((await wrongPwRes.json()).message, 'Email ou mot de passe incorrect');

  // Route privée refusée sans cookie
  const unauthMe = await fetch(`${baseUrl}/api/auth/me`);
  assert.equal(unauthMe.status, 401);

  // Connexion réussie => cookie httpOnly JWT
  const loginRes = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'marie@lubum.cd', password: 'SecretPass88' })
  });
  assert.equal(loginRes.status, 200);
  const cookieHeader = extractCookie(loginRes);

  // Accès aux routes privées avec le cookie
  const meRes = await fetch(`${baseUrl}/api/auth/me`, { headers: { Cookie: cookieHeader } });
  assert.equal(meRes.status, 200);
  assert.equal((await meRes.json()).user.email, 'marie@lubum.cd');

  const protectedRes = await fetch(`${baseUrl}/api/protected/account-overview`, {
    headers: { Cookie: cookieHeader }
  });
  assert.equal(protectedRes.status, 200);

  // Déconnexion efface le cookie
  const logoutRes = await fetch(`${baseUrl}/api/auth/logout`, {
    method: 'POST',
    headers: { Cookie: cookieHeader }
  });
  assert.equal(logoutRes.status, 200);
  const clearedCookie = logoutRes.headers.getSetCookie()[0];
  assert.match(clearedCookie, /lubumarket_session=;/);
});

test('Mot de passe oublié et réinitialisation à durée limitée', async () => {
  await fetch(`${baseUrl}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: 'reset@lubum.cd',
      password: 'OldPassword123',
      confirmPassword: 'OldPassword123'
    })
  });
  const verifyToken = extractTokenFromEmail(sentEmails[0]);
  await fetch(`${baseUrl}/api/auth/verify-email?token=${verifyToken}`, {
    headers: { Accept: 'application/json' }
  });

  const forgotRes = await fetch(`${baseUrl}/api/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'reset@lubum.cd' })
  });
  assert.equal(forgotRes.status, 200);
  assert.equal(sentEmails.length, 2);

  const resetToken = extractTokenFromEmail(sentEmails[1]);
  assert.ok(resetToken);

  const resetRes = await fetch(`${baseUrl}/api/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      token: resetToken,
      newPassword: 'NewPassword456',
      confirmPassword: 'NewPassword456'
    })
  });
  assert.equal(resetRes.status, 200);

  // Ancien mot de passe échoue, nouveau mot de passe réussit
  const oldLogin = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'reset@lubum.cd', password: 'OldPassword123' })
  });
  assert.equal(oldLogin.status, 401);

  const newLogin = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'reset@lubum.cd', password: 'NewPassword456' })
  });
  assert.equal(newLogin.status, 200);
});
