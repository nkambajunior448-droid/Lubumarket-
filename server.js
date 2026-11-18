require('dotenv').config();
const { createApp } = require('./app');

const port = Number(process.env.PORT || 4000);
const { app } = createApp();

app.listen(port, '0.0.0.0', () => {
  console.log(`[LUBUMARKET AUTH] Serveur Express + SQLite démarré sur http://0.0.0.0:${port}`);
});
