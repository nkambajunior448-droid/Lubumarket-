const crypto = require('node:crypto');
const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const { SESSION_COOKIE_NAME } = require('../middleware/authMiddleware');
const { buildVerificationEmail, buildResetPasswordEmail } = require('../mailer');

const BCRYPT_ROUNDS = 12;
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000; // 24 heures
const RESET_TTL_MS = 15 * 60 * 1000; // 15 minutes

function sha256(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex');
}

function normalizeEmail(raw) {
  return String(raw || '').trim().toLowerCase();
}

function createAuthRouter({
  stmts,
  mailer,
  requireAuth,
  jwtSecret,
  jwtExpiresIn = '7d',
  appBaseUrl = 'http://localhost:4000',
  isProduction = false
}) {
  const router = express.Router();

  const cookieOptions = {
    httpOnly: true,
    sameSite: 'lax',
    secure: isProduction,
    path: '/',
    maxAge: 7 * 24 * 60 * 60 * 1000
  };

  // 1. Inscription (email + mot de passe >= 8 chars + confirmation)
  router.post('/register', async (req, res) => {
    try {
      const email = normalizeEmail(req.body?.email);
      const password = String(req.body?.password || '');
      const confirmPassword = String(req.body?.confirmPassword || '');

      if (!EMAIL_REGEX.test(email)) {
        return res.status(400).json({
          success: false,
          message: 'Veuillez saisir une adresse email valide.'
        });
      }

      if (password.length < 8) {
        return res.status(400).json({
          success: false,
          message: 'Le mot de passe doit contenir au moins 8 caractères.'
        });
      }

      if (password !== confirmPassword) {
        return res.status(400).json({
          success: false,
          message: 'La confirmation du mot de passe ne correspond pas.'
        });
      }

      const existing = stmts.findByEmail.get(email);
      if (existing) {
        return res.status(409).json({
          success: false,
          message: 'Cette adresse email est déjà utilisée.'
        });
      }

      const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
      const rawToken = crypto.randomBytes(32).toString('hex');
      const tokenHash = sha256(rawToken);
      const expiresAt = Date.now() + VERIFICATION_TTL_MS;
      const nowIso = new Date().toISOString();

      const info = stmts.insertUser.run(
        email,
        passwordHash,
        tokenHash,
        expiresAt,
        nowIso,
        nowIso
      );

      const verifyUrl = `${appBaseUrl.replace(/\/$/, '')}/api/auth/verify-email?token=${rawToken}`;
      const mailContent = buildVerificationEmail({ email, verifyUrl });
      await mailer.sendEmail({ to: email, ...mailContent });

      return res.status(201).json({
        success: true,
        requiresEmailVerification: true,
        message: 'Compte créé avec succès ! Un lien de confirmation vient d\'être envoyé à votre adresse email.',
        user: {
          id: Number(info.lastInsertRowid),
          email,
          isVerified: false,
          createdAt: nowIso
        }
      });
    } catch (err) {
      console.error('[AUTH REGISTER ERROR]', err);
      return res.status(500).json({
        success: false,
        message: 'Erreur interne lors de l\'inscription.'
      });
    }
  });

  // 2. Vérification de l'email (via lien GET ou appel API POST)
  async function handleVerifyEmail(rawToken, res, wantsHtml) {
    const token = String(rawToken || '').trim();
    if (!token) {
      if (wantsHtml) {
        return res.status(400).send(renderHtmlPage('Lien invalide', 'Aucun jeton de confirmation fourni.', false));
      }
      return res.status(400).json({ success: false, message: 'Jeton de vérification manquant.' });
    }

    const tokenHash = sha256(token);
    const user = stmts.findByVerificationToken.get(tokenHash);
    if (!user || !user.verification_expires_at || user.verification_expires_at < Date.now()) {
      if (wantsHtml) {
        return res.status(400).send(renderHtmlPage('Lien expiré ou invalide', 'Ce lien de confirmation est invalide ou a expiré. Veuillez demander un nouveau lien depuis l\'application.', false));
      }
      return res.status(400).json({ success: false, message: 'Lien de vérification invalide ou expiré.' });
    }

    stmts.markVerified.run(new Date().toISOString(), user.id);

    if (wantsHtml) {
      return res.status(200).send(renderHtmlPage('Email confirmé !', `Votre adresse ${user.email} est maintenant vérifiée. Vous pouvez retourner sur LUBUMARKET et vous connecter.`, true));
    }
    return res.status(200).json({
      success: true,
      message: 'Adresse email confirmée ! Vous pouvez maintenant vous connecter.'
    });
  }

  router.get('/verify-email', (req, res) => {
    const acceptsHtml = (req.headers.accept || '').includes('text/html');
    return handleVerifyEmail(req.query.token, res, acceptsHtml);
  });

  router.post('/verify-email', (req, res) => {
    return handleVerifyEmail(req.body?.token, res, false);
  });

  // 3. Renvoyer le lien de confirmation
  router.post('/resend-verification', async (req, res) => {
    try {
      const email = normalizeEmail(req.body?.email);
      if (!EMAIL_REGEX.test(email)) {
        return res.status(400).json({ success: false, message: 'Adresse email invalide.' });
      }

      const user = stmts.findByEmail.get(email);
      if (user && user.is_verified === 0) {
        const rawToken = crypto.randomBytes(32).toString('hex');
        const tokenHash = sha256(rawToken);
        const expiresAt = Date.now() + VERIFICATION_TTL_MS;
        stmts.updateVerificationToken.run(tokenHash, expiresAt, new Date().toISOString(), user.id);

        const verifyUrl = `${appBaseUrl.replace(/\/$/, '')}/api/auth/verify-email?token=${rawToken}`;
        const mailContent = buildVerificationEmail({ email, verifyUrl });
        await mailer.sendEmail({ to: email, ...mailContent });
      }

      return res.status(200).json({
        success: true,
        message: 'Si ce compte existe et n\'est pas encore vérifié, un nouveau lien de confirmation a été envoyé.'
      });
    } catch (err) {
      console.error('[RESEND VERIFICATION ERROR]', err);
      return res.status(500).json({ success: false, message: 'Impossible de renvoyer l\'email.' });
    }
  });

  // 4. Connexion (erreur générique sur échec + blocage si email non vérifié + cookie httpOnly JWT)
  router.post('/login', async (req, res) => {
    try {
      const email = normalizeEmail(req.body?.email);
      const password = String(req.body?.password || '');

      if (!email || !password) {
        return res.status(401).json({
          success: false,
          message: 'Email ou mot de passe incorrect'
        });
      }

      const user = stmts.findByEmail.get(email);
      if (!user) {
        // Constant-time mitigation against timing enumeration
        await bcrypt.compare(password, '$2b$12$invalidhashpaddingstringinvalidhashpaddingstring12345');
        return res.status(401).json({
          success: false,
          message: 'Email ou mot de passe incorrect'
        });
      }

      const isPasswordValid = await bcrypt.compare(password, user.password_hash);
      if (!isPasswordValid) {
        return res.status(401).json({
          success: false,
          message: 'Email ou mot de passe incorrect'
        });
      }

      if (user.is_verified !== 1) {
        return res.status(403).json({
          success: false,
          requiresEmailVerification: true,
          message: 'Veuillez confirmer votre adresse email avant de vous connecter.'
        });
      }

      const token = jwt.sign(
        { sub: user.id, email: user.email },
        jwtSecret,
        { expiresIn: jwtExpiresIn }
      );

      res.cookie(SESSION_COOKIE_NAME, token, cookieOptions);

      return res.status(200).json({
        success: true,
        message: 'Connexion réussie.',
        user: {
          id: user.id,
          email: user.email,
          isVerified: true,
          createdAt: user.created_at
        }
      });
    } catch (err) {
      console.error('[AUTH LOGIN ERROR]', err);
      return res.status(500).json({
        success: false,
        message: 'Erreur interne lors de la connexion.'
      });
    }
  });

  // 5. Déconnexion sécurisée (efface le cookie httpOnly)
  router.post('/logout', (_req, res) => {
    res.clearCookie(SESSION_COOKIE_NAME, {
      httpOnly: true,
      sameSite: 'lax',
      secure: isProduction,
      path: '/'
    });
    return res.status(200).json({
      success: true,
      message: 'Vous avez été déconnecté avec succès.'
    });
  });

  // 6. Route privée : session courante
  router.get('/me', requireAuth, (req, res) => {
    return res.status(200).json({
      success: true,
      user: req.user
    });
  });

  // 7. Mot de passe oublié (envoi d'un lien à durée limitée : 15 minutes)
  router.post('/forgot-password', async (req, res) => {
    try {
      const email = normalizeEmail(req.body?.email);
      if (!EMAIL_REGEX.test(email)) {
        return res.status(400).json({
          success: false,
          message: 'Veuillez saisir une adresse email valide.'
        });
      }

      const user = stmts.findByEmail.get(email);
      if (user) {
        const rawToken = crypto.randomBytes(32).toString('hex');
        const tokenHash = sha256(rawToken);
        const expiresAt = Date.now() + RESET_TTL_MS;
        stmts.setResetToken.run(tokenHash, expiresAt, new Date().toISOString(), user.id);

        const resetUrl = `${appBaseUrl.replace(/\/$/, '')}/api/auth/reset-password?token=${rawToken}`;
        const mailContent = buildResetPasswordEmail({ email, resetUrl, token: rawToken });
        await mailer.sendEmail({ to: email, ...mailContent });
      }

      return res.status(200).json({
        success: true,
        message: 'Si cette adresse email est enregistrée, un lien de réinitialisation valable 15 minutes vient d\'être envoyé.'
      });
    } catch (err) {
      console.error('[FORGOT PASSWORD ERROR]', err);
      return res.status(500).json({
        success: false,
        message: 'Erreur lors de la demande de réinitialisation.'
      });
    }
  });

  // Formulaire HTML facultatif si l'utilisateur clique le lien de reset dans un navigateur
  router.get('/reset-password', (req, res) => {
    const token = String(req.query.token || '').trim();
    if (!token) {
      return res.status(400).send(renderHtmlPage('Lien invalide', 'Jeton de réinitialisation manquant.', false));
    }
    return res.status(200).send(renderResetFormHtml(token));
  });

  // 8. Validation du lien & mise à jour du mot de passe
  router.post('/reset-password', async (req, res) => {
    try {
      const token = String(req.body?.token || '').trim();
      const newPassword = String(req.body?.newPassword || req.body?.password || '');
      const confirmPassword = String(req.body?.confirmPassword || '');

      if (!token) {
        return res.status(400).json({
          success: false,
          message: 'Jeton de réinitialisation manquant.'
        });
      }

      if (newPassword.length < 8) {
        return res.status(400).json({
          success: false,
          message: 'Le nouveau mot de passe doit contenir au moins 8 caractères.'
        });
      }

      if (newPassword !== confirmPassword) {
        return res.status(400).json({
          success: false,
          message: 'La confirmation du mot de passe ne correspond pas.'
        });
      }

      const tokenHash = sha256(token);
      const user = stmts.findByResetToken.get(tokenHash);
      if (!user || !user.reset_expires_at || user.reset_expires_at < Date.now()) {
        return res.status(400).json({
          success: false,
          message: 'Lien de réinitialisation invalide ou expiré.'
        });
      }

      const newHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
      stmts.updatePasswordAfterReset.run(newHash, new Date().toISOString(), user.id);

      res.clearCookie(SESSION_COOKIE_NAME, {
        httpOnly: true,
        sameSite: 'lax',
        secure: isProduction,
        path: '/'
      });

      return res.status(200).json({
        success: true,
        message: 'Mot de passe réinitialisé avec succès ! Vous pouvez maintenant vous connecter.'
      });
    } catch (err) {
      console.error('[RESET PASSWORD ERROR]', err);
      return res.status(500).json({
        success: false,
        message: 'Erreur lors de la réinitialisation du mot de passe.'
      });
    }
  });

  return router;
}

function renderHtmlPage(title, message, isSuccess) {
  const accent = isSuccess ? '#0756B8' : '#E53935';
  return `<!DOCTYPE html>
<html lang="fr">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title}</title></head>
<body style="font-family:Arial,sans-serif;background:#f8fafc;margin:0;padding:32px;display:flex;justify-content:center;">
  <div style="max-width:460px;width:100%;background:#fff;border-radius:14px;border-top:6px solid ${accent};padding:28px;box-shadow:0 4px 18px rgba(0,0,0,.08);">
    <h2 style="color:${accent};margin-top:0;">${title}</h2>
    <p style="color:#1f2937;line-height:1.5;">${message}</p>
  </div>
</body>
</html>`;
}

function renderResetFormHtml(token) {
  const safeToken = token.replace(/[^a-fA-F0-9]/g, '');
  return `<!DOCTYPE html>
<html lang="fr">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Réinitialiser le mot de passe — LUBUMARKET</title></head>
<body style="font-family:Arial,sans-serif;background:#f8fafc;margin:0;padding:32px;display:flex;justify-content:center;">
  <form id="resetForm" style="max-width:440px;width:100%;background:#fff;border-radius:14px;border-top:6px solid #E53935;padding:28px;box-shadow:0 4px 18px rgba(0,0,0,.08);">
    <h2 style="color:#0756B8;margin-top:0;">Nouveau mot de passe</h2>
    <input type="hidden" name="token" value="${safeToken}" />
    <label style="display:block;font-size:13px;font-weight:bold;margin-bottom:6px;">Nouveau mot de passe (8 caractères min.)</label>
    <input type="password" id="pw" required minlength="8" style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;margin-bottom:14px;box-sizing:border-box;" />
    <label style="display:block;font-size:13px;font-weight:bold;margin-bottom:6px;">Confirmer le mot de passe</label>
    <input type="password" id="cpw" required minlength="8" style="width:100%;padding:10px;border:1px solid #cbd5e1;border-radius:8px;margin-bottom:18px;box-sizing:border-box;" />
    <button type="submit" style="width:100%;padding:12px;background:#0756B8;color:#fff;border:none;border-radius:8px;font-weight:bold;cursor:pointer;">Enregistrer le mot de passe</button>
    <p id="msg" style="margin-top:14px;font-size:13px;"></p>
  </form>
  <script>
    document.getElementById('resetForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const res = await fetch('/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          token: '${safeToken}',
          newPassword: document.getElementById('pw').value,
          confirmPassword: document.getElementById('cpw').value
        })
      });
      const data = await res.json();
      const el = document.getElementById('msg');
      el.style.color = data.success ? '#0756B8' : '#E53935';
      el.textContent = data.message;
    });
  </script>
</body>
</html>`;
}

module.exports = { createAuthRouter };
