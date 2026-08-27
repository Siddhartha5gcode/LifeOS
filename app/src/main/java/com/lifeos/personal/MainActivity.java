package com.lifeos.personal;

import android.Manifest;
 import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    // --- Version & Backtrace Audit Info ---
    public static final String APP_VERSION_NAME = "1.1.0";
    public static final int APP_VERSION_CODE = 2;
    public static final String BUILD_TIMESTAMP = "2026-08-18T16:06:00+05:30";
    public static final String TAG = "LifeOS_Backtrace";

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setDatabaseEnabled(true);

        webView.setTag("main_webview");
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidNative");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        requestStoragePermissions();
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    private File getStorageFolder() {
        File docsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File appFolder = new File(docsFolder, "LifeOS_Data");
        if (!appFolder.exists()) {
            appFolder.mkdirs();
        }
        return appFolder;
    }

    // --- Native JS Bridge ---
    public class WebAppInterface {

        @JavascriptInterface
        public String getAppVersionInfo() {
            return "{\"versionName\":\"" + APP_VERSION_NAME + "\",\"versionCode\":" + APP_VERSION_CODE + ",\"buildTimestamp\":\"" + BUILD_TIMESTAMP + "\"}";
        }

        @JavascriptInterface
        public void uploadBackupToGoogleDrive(String jsonString) {
            try {
                File cacheDir = getExternalCacheDir();
                File backupFile = new File(cacheDir, "LifeOS_Backup_" + System.currentTimeMillis() + ".json");
                FileWriter writer = new FileWriter(backupFile, false);
                writer.write(jsonString);
                writer.flush();
                writer.close();

                Uri contentUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", backupFile);

                Intent sendIntent = new Intent(Intent.ACTION_SEND);
                sendIntent.setType("application/json");
                sendIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(sendIntent, "Upload Backup to Google Drive");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void saveToStorageFolder(String jsonString) {
            try {
                File folder = getStorageFolder();
                File backupFile = new File(folder, "lifeos_backup.json");
                FileWriter writer = new FileWriter(backupFile, false);
                writer.write(jsonString);
                writer.flush();
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public String loadFromStorageFolder() {
            try {
                File folder = getStorageFolder();
                File backupFile = new File(folder, "lifeos_backup.json");
                if (!backupFile.exists()) return "";

                StringBuilder sb = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(backupFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                return sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        @JavascriptInterface
        public void updateAppFromUrl(String apkUrl) {
            new Thread(() -> downloadAndInstallApk(apkUrl)).start();
        }

        /**
         * driveApiCall — authenticated Google Drive REST API bridge.
         * action:
         *   "find"   — search for file by name in FOLDER_ID, returns file id or ""
         *   "read"   — download file content by Drive file id
         *   "write"  — create or overwrite file with given name & content in FOLDER_ID
         */
        @JavascriptInterface
        public String driveApiCall(String action, String fileNameOrId, String content) {
            final String FOLDER_ID     = "1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ";
            final String TOKEN_FILE    = getTokenFilePath();

            try {
                String accessToken = loadTokenField(TOKEN_FILE, "access_token");
                if (accessToken.isEmpty()) {
                    return "{\"error\":\"no_token\",\"message\":\"No OAuth token on device. Please paste your token in Account > Drive Setup.\"}";
                }

                // Execute with auto-retry on 401 (token expired)
                String result = executeDriveAction(action, fileNameOrId, content, accessToken, FOLDER_ID);

                // If 401/403 → try refresh, then retry
                if (result.contains("\"error\":\"http_401\"") || result.contains("\"error\":\"http_403\"")) {
                    String refreshToken = loadTokenField(TOKEN_FILE, "refresh_token");
                    String clientId    = loadTokenField(TOKEN_FILE, "client_id");
                    if (clientId.isEmpty()) clientId = "407408718192.apps.googleusercontent.com";

                    String newToken = refreshAccessToken(refreshToken, clientId, TOKEN_FILE);
                    if (!newToken.isEmpty()) {
                        result = executeDriveAction(action, fileNameOrId, content, newToken, FOLDER_ID);
                    } else {
                        return "{\"error\":\"token_expired\",\"message\":\"Token expired. Auto-refresh failed. Please re-enter token in Account > Drive Setup.\"}";
                    }
                }
                return result;

            } catch (Exception ex) {
                ex.printStackTrace();
                return "{\"error\":\"exception\",\"message\":\"" + ex.getMessage().replace("\"", "'") + "\"}";
            }
        }

        /** Returns path to token file in Android app internal storage */
        private String getTokenFilePath() {
            return getFilesDir().getAbsolutePath() + "/gdrive_token.json";
        }

        /**
         * Save OAuth tokens to Android internal storage.
         * Called from JS when user pastes token in the Drive Setup card.
         * accessToken  — the ya29.xxx token from OAuth Playground
         * refreshToken — the 1//04xxx refresh token (optional but recommended)
         */
        @JavascriptInterface
        public String saveOAuthToken(String accessToken, String refreshToken) {
            try {
                String clientId = "407408718192.apps.googleusercontent.com";
                String tokenJson = "{\n"
                    + "  \"access_token\": \"" + accessToken.trim()  + "\",\n"
                    + "  \"refresh_token\": \"" + refreshToken.trim() + "\",\n"
                    + "  \"client_id\": \""      + clientId             + "\"\n"
                    + "}";
                FileWriter fw = new FileWriter(getTokenFilePath(), false);
                fw.write(tokenJson); fw.flush(); fw.close();
                return "{\"status\":\"saved\",\"path\":\"" + getTokenFilePath() + "\"}";
            } catch (Exception e) {
                return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
            }
        }

        /** Returns whether a token file exists and has an access_token */
        @JavascriptInterface
        public String getOAuthTokenStatus() {
            String tokenPath = getTokenFilePath();
            String at = loadTokenField(tokenPath, "access_token");
            String rt = loadTokenField(tokenPath, "refresh_token");
            boolean hasToken = !at.isEmpty();
            String preview   = hasToken ? (at.substring(0, Math.min(20, at.length())) + "...") : "";
            return "{\"hasToken\":" + hasToken
                + ",\"hasRefresh\":" + !rt.isEmpty()
                + ",\"preview\":\"" + preview + "\""
                + ",\"path\":\"" + tokenPath + "\"}"
            ;
        }

        /**
         * openOAuthPlayground — Opens an in-app WebView dialog loading Google OAuth Playground.
         * Pre-selects drive.file scope. Injects JS that watches for access_token + refresh_token
         * in the response JSON on screen. When found, auto-saves and closes the dialog.
         */
        @JavascriptInterface
        public void openOAuthPlayground() {
            final String OAUTH_URL = "https://developers.google.com/oauthplayground/"
                + "?scope=https://www.googleapis.com/auth/drive.file"
                + "&access_type=offline&response_type=code"
                + "#step=1&scopes=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.file";

            // JS injected on every page load — watches for tokens in the page
            final String INJECT_JS =
                "(function() {" +
                "  try {" +
                "    var txt = document.body ? document.body.innerText : '';" +
                "    var atM = txt.match(/\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"/);" +
                "    var rtM = txt.match(/\\\"refresh_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"/);" +
                "    if (atM && atM[1] && rtM && rtM[1]) {" +
                "      if (!window.__lifeosTokenSaved) {" +
                "        window.__lifeosTokenSaved = true;" +
                "        if (window.OAuthBridge) {" +
                "          window.OAuthBridge.receiveOAuthTokens(atM[1], rtM[1]);" +
                "        }" +
                "      }" +
                "    }" +
                "  } catch(e) {}" +
                "})();";

            final MainActivity activity = MainActivity.this;
            activity.runOnUiThread(() -> {
                // --- Build the WebView ---
                final WebView oauthWebView = new WebView(activity);
                WebSettings ws = oauthWebView.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setLoadWithOverviewMode(true);
                ws.setUseWideViewPort(true);
                ws.setBuiltInZoomControls(true);
                ws.setDisplayZoomControls(false);
                ws.setSupportZoom(true);

                // --- AlertDialog to hold everything ---
                final AlertDialog[] dialogHolder = new AlertDialog[1];

                // --- Inner bridge to receive tokens from injected JS ---
                oauthWebView.addJavascriptInterface(new Object() {
                    @JavascriptInterface
                    public void receiveOAuthTokens(String at, String rt) {
                        String saveResult = saveOAuthToken(at, rt);
                        activity.runOnUiThread(() -> {
                            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                            Toast.makeText(activity,
                                "\u2705 OAuth tokens saved! Drive is now live.",
                                Toast.LENGTH_LONG).show();
                            // Notify the main WebView JS
                            WebView main = activity.findViewById(android.R.id.content)
                                .findViewWithTag("main_webview");
                            if (main != null) {
                                main.evaluateJavascript(
                                    "refreshTokenStatusUI(); manualDriveSyncNow();", null);
                            }
                        });
                    }
                }, "OAuthBridge");

                // --- Inject detection JS on every page load ---
                oauthWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        view.evaluateJavascript(INJECT_JS, null);
                        // Also schedule re-checks every 2 seconds (for dynamic pages)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (view.isAttachedToWindow()) view.evaluateJavascript(INJECT_JS, null);
                        }, 2000);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            if (view.isAttachedToWindow()) view.evaluateJavascript(INJECT_JS, null);
                        }, 4000);
                    }
                });

                // --- Status bar showing current URL ---
                final LinearLayout container = new LinearLayout(activity);
                container.setOrientation(LinearLayout.VERTICAL);
                container.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                final TextView statusBar = new TextView(activity);
                statusBar.setText("Loading OAuth Playground...");
                statusBar.setTextSize(10f);
                statusBar.setPadding(16, 8, 16, 8);
                statusBar.setTextColor(0xFF94a3b8);
                statusBar.setBackgroundColor(0xFF0b0f19);
                statusBar.setMaxLines(1);

                final ProgressBar progressBar = new ProgressBar(activity,
                    null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 8));

                oauthWebView.setWebChromeClient(new WebChromeClient() {
                    @Override public void onProgressChanged(WebView v, int p) {
                        progressBar.setProgress(p);
                        progressBar.setVisibility(p == 100 ? android.view.View.GONE : android.view.View.VISIBLE);
                    }
                    @Override public void onReceivedTitle(WebView v, String t) {
                        statusBar.setText(v.getUrl() != null ? v.getUrl() : t);
                    }
                });

                container.addView(statusBar);
                container.addView(progressBar);

                LinearLayout.LayoutParams wvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
                oauthWebView.setLayoutParams(wvParams);
                container.addView(oauthWebView);

                // Size the dialog to ~90% of screen
                DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                int dlgW = (int)(dm.widthPixels  * 0.95f);
                int dlgH = (int)(dm.heightPixels * 0.88f);

                AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("\uD83D\uDD11 Google OAuth Setup")
                    .setView(container)
                    .setNegativeButton("Close", (d, w) -> oauthWebView.destroy())
                    .create();
                dialogHolder[0] = dialog;

                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        dialog.getWindow().setLayout(dlgW, dlgH);
                    }
                });
                dialog.show();
                oauthWebView.loadUrl(OAUTH_URL);
            });
        }

        private String loadTokenField(String filePath, String field) {
            try {
                File f = new File(filePath);
                if (!f.exists()) return "";
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                String json = sb.toString();
                int idx = json.indexOf("\"" + field + "\"");
                if (idx < 0) return "";
                int start = json.indexOf('"', idx + field.length() + 3) + 1;
                int end   = json.indexOf('"', start);
                return (start > 0 && end > start) ? json.substring(start, end) : "";
            } catch (Exception e) { return ""; }
        }

        /** Auto-refresh access token using refresh_token. Saves new token to file. Returns new access token or "". */
        private String refreshAccessToken(String refreshToken, String clientId, String tokenFilePath) {
            if (refreshToken.isEmpty()) return "";
            try {
                String postData = "grant_type=refresh_token"
                    + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8")
                    + "&client_id="     + URLEncoder.encode(clientId, "UTF-8");

                URL url = new URL("https://oauth2.googleapis.com/token");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes("UTF-8")); os.flush();

                int code = conn.getResponseCode();
                InputStream is = (code < 400) ? conn.getInputStream() : conn.getErrorStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096]; int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                String response = baos.toString("UTF-8");

                // Parse new access_token
                int idx = response.indexOf("\"access_token\"");
                if (idx < 0) return "";
                int start = response.indexOf('"', idx + 15) + 1;
                int end   = response.indexOf('"', start);
                String newToken = response.substring(start, end);

                if (!newToken.isEmpty()) {
                    // Read existing token file JSON to preserve all fields
                    String existing = "{}";
                    File tf = new File(tokenFilePath);
                    if (tf.exists()) {
                        StringBuilder sb = new StringBuilder();
                        BufferedReader br = new BufferedReader(new FileReader(tf));
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        existing = sb.toString().trim();
                    }
                    // Replace or inject access_token field
                    String updated;
                    if (existing.contains("\"access_token\"")) {
                        updated = existing.replaceAll(
                            "\"access_token\"\\s*:\\s*\"[^\"]*\"",
                            "\"access_token\": \"" + newToken + "\""
                        );
                    } else {
                        // Insert before closing brace
                        int close = existing.lastIndexOf('}');
                        updated = existing.substring(0, close)
                            + (existing.trim().equals("{}") ? "" : ",")
                            + "\n  \"access_token\": \"" + newToken + "\"\n}";
                    }
                    FileWriter fw = new FileWriter(tokenFilePath, false);
                    fw.write(updated); fw.flush(); fw.close();
                }
                return newToken;

            } catch (Exception e) {
                e.printStackTrace();
                return "";
            }
        }

        /** Core Drive REST API execution — used by driveApiCall() with auto-retry */
        private String executeDriveAction(String action, String fileNameOrId, String content, String accessToken, String FOLDER_ID) throws Exception {
            if ("find".equals(action)) {
                String query = URLEncoder.encode(
                    "'" + FOLDER_ID + "' in parents and name = '" + fileNameOrId + "' and trashed = false",
                    "UTF-8");
                URL url = new URL("https://www.googleapis.com/drive/v3/files?q=" + query + "&fields=files(id,name)");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int len;
                    while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                    return baos.toString("UTF-8");
                }
                return "{\"error\":\"http_" + code + "\",\"files\":[]}";

            } else if ("read".equals(action)) {
                URL url = new URL("https://www.googleapis.com/drive/v3/files/" + fileNameOrId + "?alt=media");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int len;
                    while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                    return baos.toString("UTF-8");
                }
                return "{\"error\":\"http_" + code + "\"}";

            } else if ("write".equals(action)) {
                String existingId = "";
                String query = URLEncoder.encode(
                    "'" + FOLDER_ID + "' in parents and name = '" + fileNameOrId + "' and trashed = false",
                    "UTF-8");
                URL findUrl = new URL("https://www.googleapis.com/drive/v3/files?q=" + query + "&fields=files(id)");
                HttpURLConnection findConn = (HttpURLConnection) findUrl.openConnection();
                findConn.setRequestProperty("Authorization", "Bearer " + accessToken);
                if (findConn.getResponseCode() == 200) {
                    InputStream fis = findConn.getInputStream();
                    ByteArrayOutputStream fbaos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int len;
                    while ((len = fis.read(buf)) != -1) fbaos.write(buf, 0, len);
                    String findResult = fbaos.toString("UTF-8");
                    int idxId = findResult.indexOf("\"id\"");
                    if (idxId >= 0) {
                        int s = findResult.indexOf('"', idxId + 5) + 1;
                        int e = findResult.indexOf('"', s);
                        existingId = findResult.substring(s, e);
                    }
                }

                byte[] contentBytes = content.getBytes("UTF-8");
                if (!existingId.isEmpty()) {
                    URL patchUrl = new URL("https://www.googleapis.com/upload/drive/v3/files/" + existingId + "?uploadType=media");
                    HttpURLConnection patchConn = (HttpURLConnection) patchUrl.openConnection();
                    patchConn.setRequestMethod("PATCH");
                    patchConn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    patchConn.setRequestProperty("Content-Type", "application/json");
                    patchConn.setRequestProperty("Content-Length", String.valueOf(contentBytes.length));
                    patchConn.setDoOutput(true);
                    OutputStream os = patchConn.getOutputStream();
                    os.write(contentBytes); os.flush();
                    int code = patchConn.getResponseCode();
                    return "{\"status\":\"updated\",\"httpCode\":" + code + ",\"id\":\"" + existingId + "\"}";
                } else {
                    String boundary = "----LifeOS_Drive_Boundary";
                    String metadata = "{\"name\":\"" + fileNameOrId + "\",\"parents\":[\"" + FOLDER_ID + "\"]}";
                    String CRLF = "\r\n";
                    byte[] metaPart = ("--" + boundary + CRLF + "Content-Type: application/json; charset=UTF-8" + CRLF + CRLF + metadata + CRLF).getBytes("UTF-8");
                    byte[] bodyPart = ("--" + boundary + CRLF + "Content-Type: application/json" + CRLF + CRLF).getBytes("UTF-8");
                    byte[] endPart  = (CRLF + "--" + boundary + "--").getBytes("UTF-8");

                    URL createUrl = new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart");
                    HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
                    createConn.setRequestMethod("POST");
                    createConn.setRequestProperty("Authorization", "Bearer " + accessToken);
                    createConn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
                    createConn.setDoOutput(true);
                    DataOutputStream dos = new DataOutputStream(createConn.getOutputStream());
                    dos.write(metaPart); dos.write(bodyPart);
                    dos.write(contentBytes); dos.write(endPart); dos.flush();
                    int code = createConn.getResponseCode();
                    InputStream is = (code < 400) ? createConn.getInputStream() : createConn.getErrorStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096]; int len;
                    while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                    return baos.toString("UTF-8");
                }
            }
            return "{\"error\":\"unknown_action\"}";
        }
    }


    // --- Background APK Downloader & Package Installer ---
    private void downloadAndInstallApk(String urlStr) {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (urlStr == null || urlStr.trim().isEmpty()) {
            mainHandler.post(() -> Toast.makeText(MainActivity.this, "❌ Update URL is empty", Toast.LENGTH_SHORT).show());
            return;
        }

        final String finalUrlStr = urlStr.trim();

        // If it's a Google Drive folder URL, open folder directly in browser
        if (finalUrlStr.contains("/folders/")) {
            mainHandler.post(() -> {
                Toast.makeText(MainActivity.this, "🌐 Opening Google Drive Folder to download latest APK...", Toast.LENGTH_SHORT).show();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrlStr));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            });
            return;
        }

        mainHandler.post(() -> Toast.makeText(MainActivity.this, "📥 Downloading APK update...", Toast.LENGTH_LONG).show());

        new Thread(() -> {
            try {
                String downloadUrl = finalUrlStr;
                // Convert standard Google Drive file link to direct download stream
                if (downloadUrl.contains("drive.google.com") && downloadUrl.contains("/d/")) {
                    int start = downloadUrl.indexOf("/d/") + 3;
                    int end = downloadUrl.indexOf("/", start);
                    if (end == -1) end = downloadUrl.indexOf("?", start);
                    if (end == -1) end = downloadUrl.length();
                    String fileId = downloadUrl.substring(start, end);
                    downloadUrl = "https://drive.google.com/uc?export=download&confirm=no_antivirus&id=" + fileId;
                }

                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)");
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                File apkFile = new File(getExternalCacheDir(), "update.apk");
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[4096];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
                is.close();

                // Validate that file is a real APK (zip format starting with 'PK')
                boolean isValidApk = false;
                if (apkFile.exists() && apkFile.length() > 1000) {
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(apkFile)) {
                        byte[] header = new byte[2];
                        if (fis.read(header) == 2 && header[0] == 'P' && header[1] == 'K') {
                            isValidApk = true;
                        }
                    }
                }

                if (isValidApk) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "✅ Download Complete! Installing update...", Toast.LENGTH_SHORT).show();
                        installApk(apkFile);
                    });
                } else {
                    // Fallback to browser if server returned an HTML download page
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "🌐 Redirecting to Browser Download...", Toast.LENGTH_SHORT).show();
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrlStr));
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(browserIntent);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(MainActivity.this, "🌐 Opening Download Link in Browser...", Toast.LENGTH_SHORT).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalUrlStr));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Intent reqIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    reqIntent.setData(Uri.parse("package:" + getPackageName()));
                    reqIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(reqIntent);
                    Toast.makeText(this, "⚠️ Please grant 'Install Unknown Apps' permission, then click update again.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", apkFile);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "❌ Package Installation Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
