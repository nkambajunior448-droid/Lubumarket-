const jwt = require('jsonwebtoken');

const SESSION_COOKIE_NAME = 'lubumarket_session';

function createAuthMiddleware(jwtSecret, stmts) {
  return function requireAuth(req, res, next) {
    const token = req.cookies?.[SESSION_COOKIE_NAME];
    if (!token) {
      return res.status(401).json({
        success: false,
        message: 'Authentification requise. Veuillez vous connecter.'
      });
    }

    try {
      const payload = jwt.verify(token, jwtSecret);
      const user = stmts.findById.get(payload.sub);
      if (!user) {
        return res.status(401).json({
          success: false,
          message: 'Session invalide ou compte introuvable.'
        });
      }
      if (user.is_verified !== 1) {
        return res.status(403).json({
          success: false,
          message: 'Veuillez confirmer votre adresse email avant d\'accéder à cette ressource.'
        });
      }
      req.user = {
        id: user.id,
        email: user.email,
        isVerified: user.is_verified === 1,
        createdAt: user.created_at
      };
      return next();
    } catch (_err) {
      return res.status(401).json({
        success: false,
        message: 'Session expirée ou invalide. Veuillez vous reconnecter.'
      });
    }
  };
}

module.exports = { SESSION_COOKIE_NAME, createAuthMiddleware };
