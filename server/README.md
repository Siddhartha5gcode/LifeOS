# 🚀 LifeOS Node.js Express Sync Server

A lightweight, low-resource REST API server designed to run on **Render Free Tier** for syncing and backing up **LifeOS** user data across devices.

---

## ⚡ Features
- **Ultra Low Memory**: Uses only ~30-40 MB of RAM (fits easily in Render's 512 MB limit).
- **Zero Configuration**: Ready to deploy directly to Render.
- **REST Endpoints**:
  - `GET /health`: Health check & uptime monitor.
  - `GET /api/sync/:userId`: Fetch user's latest synced LifeOS state.
  - `POST /api/sync/:userId`: Upload/sync LifeOS data (Expenses, Habits, Loan, Milk, Lend records).
  - `GET /api/backups/:userId`: Retrieve historical snapshots.
  - `DELETE /api/sync/:userId`: Clear sync data for a user.

---

## 🛠️ Deploying to Render

1. Go to [Render Dashboard](https://dashboard.render.com) $\rightarrow$ **New +** $\rightarrow$ **Web Service**.
2. Connect your Git repository.
3. Configure the settings:
   - **Root Directory**: `server`
   - **Environment**: `Node`
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free`

4. Click **Create Web Service**. Your server URL will be:
   `https://<your-app-name>.onrender.com`

---

## 📱 Integrating with LifeOS Frontend (`index.html`)

In `index.html`, trigger automatic or manual sync calls:

```js
// Example Sync Push
async function syncLifeOSToServer(userId, lifeOsData) {
  const SERVER_URL = 'https://<your-app-name>.onrender.com';
  const response = await fetch(`${SERVER_URL}/api/sync/${userId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ data: lifeOsData })
  });
  return await response.json();
}
```
