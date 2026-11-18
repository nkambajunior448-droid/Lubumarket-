const nodemailer = require('nodemailer');

function createMailer(customSender = null) {
  if (typeof customSender === 'function') {
    return { sendEmail: customSender };
  }

  let smtpTransporter = null;
  if (process.env.SMTP_HOST && process.env.SMTP_USER && process.env.SMTP_PASS) {
    smtpTransporter = nodemailer.createTransport({
      host: process.env.SMTP_HOST,
      port: Number(process.env.SMTP_PORT || 587),
      secure: String(process.env.SMTP_SECURE || 'false') === 'true',
      auth: {
        user: process.env.SMTP_USER,
        pass: process.env.SMTP_PASS
      }
    });
  }

  async function sendEmail({ to, subject, html, text }) {
    const from = process.env.EMAIL_FROM || 'Lubumarket <no-reply@lubumarket.cd>';

    // 1. Prefer Resend HTTP API if configured
    if (process.env.RESEND_API_KEY) {
      const response = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${process.env.RESEND_API_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ from, to, subject, html, text })
      });
      if (!response.ok) {
        const body = await response.text();
        throw new Error(`Erreur d'envoi Resend (${response.status}): ${body}`);
      }
      return response.json();
    }

    // 2. Otherwise use configured SMTP / Nodemailer transporter
    if (smtpTransporter) {
      return smtpTransporter.sendMail({ from, to, subject, html, text });
    }

    // 3. Local dev/test fallback: JSON stream transport (never crashes local development without SMTP keys)
    const devTransport = nodemailer.createTransport({ jsonTransport: true });
    const info = await devTransport.sendMail({ from, to, subject, html, text });
    if (process.env.NODE_ENV !== 'test') {
      console.log(`[MAILER DEV] Email prêt pour ${to} | Sujet: ${subject}`);
      console.log(info.message);
    }
    return info;
  }

  return { sendEmail };
}

function buildVerificationEmail({ email, verifyUrl }) {
  const subject = 'Confirmez votre adresse email — LUBUMARKET';
  const text = [
    'Bienvenue sur LUBUMARKET !',
    '',
    `Pour activer votre compte (${email}), cliquez sur le lien ci-dessous (valable 24 heures) :`,
    verifyUrl,
    '',
    'Si vous n\'êtes pas à l\'origine de cette inscription, ignorez cet email.'
  ].join('\n');

  const html = `
    <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden;">
      <div style="background: #0756B8; color: #ffffff; padding: 20px;">
        <h2 style="margin: 0;">LUBUMARKET</h2>
        <p style="margin: 4px 0 0; font-size: 13px;">Confirmation de votre adresse email</p>
      </div>
      <div style="padding: 24px; color: #111827;">
        <p>Bonjour,</p>
        <p>Merci pour votre inscription sur <strong>LUBUMARKET</strong> avec l'adresse <strong>${email}</strong>.</p>
        <p>Cliquez sur le bouton ci-dessous pour vérifier votre adresse email et activer votre connexion :</p>
        <p style="text-align: center; margin: 28px 0;">
          <a href="${verifyUrl}" style="background: #0756B8; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;">
            Confirmer mon adresse email
          </a>
        </p>
        <p style="font-size: 12px; color: #6b7280;">Ce lien expire dans 24 heures. Lien direct : ${verifyUrl}</p>
      </div>
    </div>
  `;
  return { subject, text, html };
}

function buildResetPasswordEmail({ email, resetUrl, token }) {
  const subject = 'Réinitialisation de votre mot de passe — LUBUMARKET';
  const text = [
    'Demande de réinitialisation de mot de passe LUBUMARKET',
    '',
    `Pour réinitialiser le mot de passe du compte ${email}, utilisez ce lien (valable 15 minutes) :`,
    resetUrl,
    '',
    `Jeton de réinitialisation (si saisi dans l'application Android) : ${token}`,
    '',
    'Si vous n\'avez pas demandé cette réinitialisation, ignorez cet email.'
  ].join('\n');

  const html = `
    <div style="font-family: Arial, sans-serif; max-width: 560px; margin: 0 auto; border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden;">
      <div style="background: #E53935; color: #ffffff; padding: 20px;">
        <h2 style="margin: 0;">LUBUMARKET — Sécurité</h2>
        <p style="margin: 4px 0 0; font-size: 13px;">Réinitialisation du mot de passe</p>
      </div>
      <div style="padding: 24px; color: #111827;">
        <p>Bonjour,</p>
        <p>Une demande de réinitialisation a été effectuée pour <strong>${email}</strong>.</p>
        <p style="text-align: center; margin: 28px 0;">
          <a href="${resetUrl}" style="background: #E53935; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 8px; font-weight: bold;">
            Réinitialiser mon mot de passe
          </a>
        </p>
        <p style="font-size: 12px; color: #6b7280;">Ce lien expire dans <strong>15 minutes</strong>.</p>
        <p style="font-size: 12px; color: #374151;">Code/jeton à copier si demandé dans l'application : <code>${token}</code></p>
      </div>
    </div>
  `;
  return { subject, text, html };
}

module.exports = { createMailer, buildVerificationEmail, buildResetPasswordEmail };
