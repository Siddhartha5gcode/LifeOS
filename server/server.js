const express = require('express');
const cors    = require('cors');
const { Pool } = require('pg');

const app  = express();
const PORT = process.env.PORT || 10000;

// ─── Database Connection ──────────────────────────────────────────────────────
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: false }   // Required for Supabase / Render hosted PG
});

// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '10mb' }));

// ─── Bootstrap DB Table ───────────────────────────────────────────────────────
async function initDB() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS lifeos_sync (
      user_id     TEXT PRIMARY KEY,
      data        JSONB        NOT NULL DEFAULT '{}',
      version     TEXT         NOT NULL DEFAULT '1.0',
      last_synced TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
      backups     JSONB        NOT NULL DEFAULT '[]'
    );
  `);
  console.log('✅ Database table ready: lifeos_sync');
}

// ─── Routes ───────────────────────────────────────────────────────────────────

// 1. Health check
app.get('/health', async (req, res) => {
  try {
    const result = await pool.query('SELECT NOW() AS db_time');
    res.json({
      status:        'ok',
      service:       'LifeOS Sync Server',
      uptimeSeconds: Math.floor(process.uptime()),
      db_time:       result.rows[0].db_time,
      timestamp:     new Date().toISOString()
    });
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

// 2. Fetch user sync data
app.get('/api/sync/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await pool.query(
      'SELECT user_id, data, version, last_synced FROM lifeos_sync WHERE user_id = $1',
      [userId]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'User sync data not found', userId });
    }
    const row = result.rows[0];
    res.json({
      success:    true,
      userId:     row.user_id,
      lastSynced: row.last_synced,
      version:    row.version,
      data:       row.data
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 3. Push / upsert user sync data
app.post('/api/sync/:userId', async (req, res) => {
  const { userId } = req.params;
  const { data, version = '1.0' } = req.body;

  if (!data || typeof data !== 'object') {
    return res.status(400).json({ error: 'Invalid sync data payload' });
  }

  try {
    // Fetch existing row to rotate backups
    const existing = await pool.query(
      'SELECT data, backups FROM lifeos_sync WHERE user_id = $1',
      [userId]
    );

    let backups = [];
    if (existing.rows.length > 0) {
      const prev = existing.rows[0];
      backups = Array.isArray(prev.backups) ? prev.backups : [];
      // Push old data into backups (keep last 10)
      backups.unshift({ timestamp: new Date().toISOString(), data: prev.data });
      if (backups.length > 10) backups = backups.slice(0, 10);
    }

    // Upsert
    await pool.query(`
      INSERT INTO lifeos_sync (user_id, data, version, last_synced, backups)
      VALUES ($1, $2, $3, NOW(), $4)
      ON CONFLICT (user_id) DO UPDATE
        SET data        = EXCLUDED.data,
            version     = EXCLUDED.version,
            last_synced = NOW(),
            backups     = EXCLUDED.backups
    `, [userId, JSON.stringify(data), version, JSON.stringify(backups)]);

    const now = new Date().toISOString();
    res.json({ success: true, userId, lastSynced: now, message: 'LifeOS data synced successfully' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 4. List historical backups for a user
app.get('/api/backups/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await pool.query(
      'SELECT backups FROM lifeos_sync WHERE user_id = $1',
      [userId]
    );
    if (result.rows.length === 0) return res.json({ success: true, userId, backups: [] });

    const backups = result.rows[0].backups || [];
    const list = backups.map((b, idx) => ({
      id:        idx + 1,
      timestamp: b.timestamp,
      sizeBytes: JSON.stringify(b.data).length
    }));
    res.json({ success: true, userId, backups: list });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 5. Delete sync data for a user
app.delete('/api/sync/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    await pool.query('DELETE FROM lifeos_sync WHERE user_id = $1', [userId]);
    res.json({ success: true, message: `Data cleared for ${userId}` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ─── Start ────────────────────────────────────────────────────────────────────
initDB()
  .then(() => {
    app.listen(PORT, () => {
      console.log(`🚀 LifeOS Sync Server running on port ${PORT}`);
    });
  })
  .catch(err => {
    console.error('❌ Failed to initialise DB:', err.message);
    process.exit(1);
  });
