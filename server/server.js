const express = require('express');
const cors    = require('cors');
const https   = require('https');

const app  = express();
const PORT = process.env.PORT || 10000;

// ─── Supabase Config (set these in Render Environment Variables) ──────────────
const SUPABASE_URL = (process.env.SUPABASE_URL || '').trim().replace(/\/$/, '');
const SUPABASE_KEY = (process.env.SUPABASE_KEY || '').trim();
const TABLE        = 'lifeos_sync';

// ─── Validate Env Vars ────────────────────────────────────────────────────────
console.log('SUPABASE_URL set:', !!SUPABASE_URL, SUPABASE_URL ? '→ ' + SUPABASE_URL : '← MISSING!');
console.log('SUPABASE_KEY set:', !!SUPABASE_KEY, SUPABASE_KEY ? '→ [hidden]' : '← MISSING!');

if (!SUPABASE_URL || !SUPABASE_KEY) {
  console.error('❌ Missing required environment variables: SUPABASE_URL and/or SUPABASE_KEY');
  console.error('   Set them in Render → Environment tab');
  process.exit(1);
}


// ─── Middleware ───────────────────────────────────────────────────────────────
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '10mb' }));

// ─── Supabase REST Helper ─────────────────────────────────────────────────────
function supabase(method, path, body) {
  return new Promise((resolve, reject) => {
    const url  = new URL(SUPABASE_URL + '/rest/v1/' + path);
    const data = body ? JSON.stringify(body) : null;
    const opts = {
      hostname: url.hostname,
      path:     url.pathname + url.search,
      method,
      headers: {
        'apikey':         SUPABASE_KEY,
        'Authorization':  'Bearer ' + SUPABASE_KEY,
        'Content-Type':   'application/json',
        'Prefer':         method === 'POST' ? 'resolution=merge-duplicates,return=representation' : 'return=representation'
      }
    };
    if (data) opts.headers['Content-Length'] = Buffer.byteLength(data);

    const req = https.request(opts, res => {
      let raw = '';
      res.on('data', c => raw += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: raw ? JSON.parse(raw) : null }); }
        catch(e) { resolve({ status: res.statusCode, data: raw }); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

// ─── Ensure Table Exists via Supabase SQL API ─────────────────────────────────
async function initDB() {
  // Try a simple select to test connection
  const test = await supabase('GET', TABLE + '?limit=1', null);
  if (test.status === 200 || test.status === 206) {
    console.log('✅ Connected to Supabase. Table lifeos_sync ready.');
  } else if (test.status === 404 || (test.data && test.data.code === '42P01')) {
    // Table doesn't exist — create it via SQL
    console.log('⚠️ Table not found. Creating lifeos_sync via SQL API...');
    const sql = await supabase('POST', '../rpc/exec', {
      sql: `CREATE TABLE IF NOT EXISTS lifeos_sync (
        user_id     TEXT PRIMARY KEY,
        data        JSONB NOT NULL DEFAULT '{}',
        version     TEXT NOT NULL DEFAULT '1.0',
        last_synced TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        backups     JSONB NOT NULL DEFAULT '[]'
      );`
    });
    console.log('Table creation result:', sql.status, JSON.stringify(sql.data));
  } else {
    throw new Error('Supabase connection failed: ' + JSON.stringify(test.data));
  }
}

// ─── Routes ───────────────────────────────────────────────────────────────────

// 1. Health check
app.get('/health', async (req, res) => {
  try {
    const test = await supabase('GET', TABLE + '?limit=1', null);
    res.json({
      status:        'ok',
      service:       'LifeOS Sync Server',
      uptimeSeconds: Math.floor(process.uptime()),
      supabase:      test.status === 200 || test.status === 206 ? 'connected' : 'error',
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
    const result = await supabase('GET', `${TABLE}?user_id=eq.${encodeURIComponent(userId)}&select=user_id,data,version,last_synced`, null);
    if (!result.data || result.data.length === 0) {
      return res.status(404).json({ error: 'User sync data not found', userId });
    }
    const row = result.data[0];
    res.json({ success: true, userId: row.user_id, lastSynced: row.last_synced, version: row.version, data: row.data });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 3. Push / upsert user sync data
app.post('/api/sync/:userId', async (req, res) => {
  const { userId } = req.params;
  const { data, version = '1.0' } = req.body;
  if (!data || typeof data !== 'object') return res.status(400).json({ error: 'Invalid sync data payload' });

  try {
    // Get existing to rotate backups
    const existing = await supabase('GET', `${TABLE}?user_id=eq.${encodeURIComponent(userId)}&select=data,backups`, null);
    let backups = [];
    if (existing.data && existing.data.length > 0) {
      const prev = existing.data[0];
      backups = Array.isArray(prev.backups) ? prev.backups : [];
      backups.unshift({ timestamp: new Date().toISOString(), data: prev.data });
      if (backups.length > 10) backups = backups.slice(0, 10);
    }

    // Upsert via POST with Prefer: resolution=merge-duplicates
    const upsert = await supabase('POST', TABLE, {
      user_id: userId, data, version,
      last_synced: new Date().toISOString(),
      backups
    });

    if (upsert.status >= 200 && upsert.status < 300) {
      res.json({ success: true, userId, lastSynced: new Date().toISOString(), message: 'LifeOS data synced successfully' });
    } else {
      throw new Error('Upsert failed: ' + JSON.stringify(upsert.data));
    }
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 4. List historical backups
app.get('/api/backups/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    const result = await supabase('GET', `${TABLE}?user_id=eq.${encodeURIComponent(userId)}&select=backups`, null);
    if (!result.data || result.data.length === 0) return res.json({ success: true, userId, backups: [] });
    const backups = result.data[0].backups || [];
    res.json({ success: true, userId, backups: backups.map((b, i) => ({ id: i+1, timestamp: b.timestamp, sizeBytes: JSON.stringify(b.data).length })) });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// 5. Delete sync data
app.delete('/api/sync/:userId', async (req, res) => {
  const { userId } = req.params;
  try {
    await supabase('DELETE', `${TABLE}?user_id=eq.${encodeURIComponent(userId)}`, null);
    res.json({ success: true, message: `Data cleared for ${userId}` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ─── Start ────────────────────────────────────────────────────────────────────
initDB()
  .then(() => app.listen(PORT, () => console.log(`🚀 LifeOS Sync Server running on port ${PORT}`)))
  .catch(err => { console.error('❌ Failed to initialise:', err.message); process.exit(1); });
