const express = require('express');
const cookieParser = require('cookie-parser');
const cors = require('cors');
const { createDatabase } = require('./db');
const { createMailer } = require('./mailer');
const { createAuthMiddleware } = require('./middleware/authMiddleware');
const { createAuthRouter } = require('./routes/authRoutes');

function createApp(options = {}) {
  const jwtSecret = options.jwtSecret || process.env.JWT_SECRET || 'dev-lubumarket-jwt-secret-change-in-prod';
  const jwtExpiresIn = options.jwtExpiresIn || process.env.JWT_EXPIRES_IN || '7d';
  const appBaseUrl = options.appBaseUrl || process.env.APP_BASE_URL || 'http://localhost:4000';
  const isProduction = options.isProduction ?? (process.env.NODE_ENV === 'production');

  const { db, stmts } = createDatabase(options.dbPath);
  const mailer = createMailer(options.customEmailSender);
  const requireAuth = createAuthMiddleware(jwtSecret, stmts);

  const app = express();
  app.use(cors({
    origin: process.env.CORS_ORIGIN || true,
    credentials: true
  }));
  app.use(express.json({ limit: '1mb' }));
  app.use(cookieParser());

  app.get('/health', (_req, res) => {
    res.json({ ok: true, service: 'lubumarket-auth-sqlite' });
  });

  app.use('/api/auth', createAuthRouter({
    stmts,
    mailer,
    requireAuth,
    jwtSecret,
    jwtExpiresIn,
    appBaseUrl,
    isProduction
  }));

  // Exemple de route privée protégée par JWT dans cookie httpOnly
  app.get('/api/protected/account-overview', requireAuth, (req, res) => {
    res.json({
      success: true,
      message: 'Accès autorisé à la route protégée.',
      user: req.user
    });
  });

  return { app, db, stmts };
}

module.exports = { createApp };
