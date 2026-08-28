const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 10000;
const DATA_DIR = path.join(__dirname, 'data');
const DATA_FILE = path.join(DATA_DIR, 'lifeos_data.json');

// Middleware
app.use(cors({ origin: '*' }));
app.use(express.json({ limit: '10mb' }));

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// In-memory cache + file sync helper
let db = {};
if (fs.existsSync(DATA_FILE)) {
  try {
    db = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  } catch (err) {
    console.error('Error loading initial data file:', err.message);
    db = {};
  }
}

function saveDb() {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2), 'utf8');
  } catch (err) {
    console.error('Error saving data file:', err.message);
  }
}

// Routes
// 1. Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'LifeOS Sync Server',
    uptimeSeconds: Math.floor(process.uptime()),
    timestamp: new Date().toISOString()
  });
});

// 2. Fetch user sync data
app.get('/api/sync/:userId', (req, res) => {
  const { userId } = req.params;
  const userData = db[userId];

  if (!userData) {
    return res.status(404).json({ error: 'User sync data not found', userId });
  }

  res.json({
    success: true,
    userId,
    lastSynced: userData.lastSynced,
    version: userData.version || '1.0',
    data: userData.data
  });
});

// 3. Update/Push user sync data
app.post('/api/sync/:userId', (req, res) => {
  const { userId } = req.params;
  const { data, version = '1.0' } = req.body;

  if (!data || typeof data !== 'object') {
    return res.status(400).json({ error: 'Invalid sync data payload' });
  }

  const now = new Date().toISOString();

  if (!db[userId]) {
    db[userId] = { backups: [] };
  }

  // Preserve historical backups limit to 10
  const existingBackups = db[userId].backups || [];
  if (db[userId].data) {
    existingBackups.unshift({
      timestamp: db[userId].lastSynced || now,
      data: db[userId].data
    });
  }

  db[userId] = {
    lastSynced: now,
    version,
    data,
    backups: existingBackups.slice(0, 10)
  };

  saveDb();

  res.json({
    success: true,
    userId,
    lastSynced: now,
    message: 'LifeOS data synced successfully'
  });
});

// 4. List user historical backups
app.get('/api/backups/:userId', (req, res) => {
  const { userId } = req.params;
  const userData = db[userId];

  if (!userData || !userData.backups) {
    return res.json({ success: true, userId, backups: [] });
  }

  const backupList = userData.backups.map((b, idx) => ({
    id: idx + 1,
    timestamp: b.timestamp,
    sizeBytes: JSON.stringify(b.data).length
  }));

  res.json({ success: true, userId, backups: backupList });
});

// 5. Clear sync data for a user
app.delete('/api/sync/:userId', (req, res) => {
  const { userId } = req.params;
  if (db[userId]) {
    delete db[userId];
    saveDb();
  }
  res.json({ success: true, message: `Data cleared for ${userId}` });
});

// Start server
app.listen(PORT, () => {
  console.log(`🚀 LifeOS Sync Server running on port ${PORT}`);
});
