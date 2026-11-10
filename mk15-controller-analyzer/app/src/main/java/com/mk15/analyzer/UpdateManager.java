package com.mk15.analyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

final class UpdateManager {
    private static final String API = "https://api.github.com/repos/dmitriy2403iskan-star/sokil-page/releases/latest";
    private static final String PREFS = "mk15_update_state";
    private static final String KEY_ID = "download_id";
    private static final String KEY_HASH = "expected_sha256";
    private static final String KEY_FILE = "download_file";
    private static final String KEY_TARGET_VERSION = "target_version";
    private static final String KEY_LAST_VERSION = "last_version_code";

    static void onAppStart(Activity activity) {
        android.content.SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int previous = p.getInt(KEY_LAST_VERSION, 0);
        if (previous > 0 && BuildConfig.VERSION_CODE > previous) {
            record(activity, "update_install_success_detected; from=" + previous + "; to=" + BuildConfig.VERSION_CODE);
        }
        p.edit().putInt(KEY_LAST_VERSION, BuildConfig.VERSION_CODE).apply();
        resumePendingDownload(activity);
    }

    private static void resumePendingDownload(Activity activity) {
        android.content.SharedPreferences p = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long id = p.getLong(KEY_ID, -1);
        if (id < 0) return;
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            android.database.Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id));
            if (cursor == null) return;
            int status;
            try {
                if (!cursor.moveToFirst()) return;
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            } finally { cursor.close(); }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                record(activity, "pending_download_found_on_start; id=" + id);
                finishDownload(activity, id);
            } else if (status == DownloadManager.STATUS_FAILED) {
                record(activity, "pending_download_failed_before_app_resume; id=" + id);
                p.edit().remove(KEY_ID).apply();
            }
        } catch (Exception e) {
            record(activity, "pending_download_check_failed; " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String KEY_LOG = "event_log";

    static void record(Context context, String event) {
        try {
            android.content.SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String old = p.getString(KEY_LOG, "");
            String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date()) + " | " + event + "\\n";
            String next = old + line;
            String[] lines = next.split("\\n");
            if (lines.length > 60) {
                StringBuilder kept = new StringBuilder();
                for (int i = lines.length - 60; i < lines.length; i++)
                    if (!lines[i].isEmpty()) kept.append(lines[i]).append("\\n");
                next = kept.toString();
            }
            p.edit().putString(KEY_LOG, next).apply();
        } catch (Exception ignored) {}
    }

    static void appendDiagnostics(Context context, StringBuilder report) {
        report.append("\\n=== UPDATE HISTORY ===\\n");
        String log = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOG, "");
        report.append(log.isEmpty() ? "No update events recorded.\\n" : log);
        report.append("Current app version: ").append(BuildConfig.VERSION_NAME)
            .append(" (code ").append(BuildConfig.VERSION_CODE).append(")\\n");
    }

    private UpdateManager() {}

    static void checkForUpdates(Activity activity, boolean manual) {
        if (activity.isFinishing()) return;
        record(activity, manual ? "manual_update_check_started" : "startup_update_check_started");
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestMethod("GET");
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "MK15-Toolkit-Android");
                int code = c.getResponseCode();
                if (code != 200) throw new Exception("GitHub HTTP " + code);
                String json = readAll(c.getInputStream());
                JSONObject release = new JSONObject(json);
                String tag = release.optString("tag_name", "");
                int remoteCode = parseBuildNumber(tag);
                JSONArray assets = release.optJSONArray("assets");
                JSONObject apk = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject item = assets.optJSONObject(i);
                        if (item != null && "app-release.apk".equals(item.optString("name"))) {
                            apk = item;
                            break;
                        }
                    }
                }
                if (remoteCode <= BuildConfig.VERSION_CODE || apk == null) {
                    record(activity, "no_update_available_or_release_asset_missing; remoteCode=" + remoteCode);
                    if (manual) MAIN.post(() -> Toast.makeText(activity, "Обновлений пока нет.", Toast.LENGTH_LONG).show());
                    return;
                }
                String url = apk.optString("browser_download_url", "");
                String digest = apk.optString("digest", "");
                if (url.isEmpty() || !digest.startsWith("sha256:")) throw new Exception("У релиза нет APK или SHA-256");
                String hash = digest.substring("sha256:".length()).toLowerCase();
                String version = release.optString("name", tag);
                String notes = release.optString("body", "");
                record(activity, "update_available; versionCode=" + remoteCode);
                MAIN.post(() -> {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                        .setTitle("Доступно обновление")
                        .setMessage(version + "\nУстановлена версия: " + BuildConfig.VERSION_NAME +
                            "\nНовая версия: " + remoteCode +
                            (notes.isEmpty() ? "" : "\n\n" + notes))
                        .setPositiveButton("Скачать обновление", (d, w) -> download(activity, url, hash, remoteCode))
                        .setNegativeButton("Позже", null)
                        .show();
                });
            } catch (Exception e) {
                record(activity, "update_check_failed; " + e.getClass().getSimpleName() + ": " + e.getMessage());
                if (manual) MAIN.post(() -> Toast.makeText(activity, "Не удалось проверить обновления: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    private static int parseBuildNumber(String tag) {
        try {
            int dash = tag.lastIndexOf('-');
            return Integer.parseInt(tag.substring(dash + 1));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream input = in; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static void download(Activity activity, String url, String expectedHash, int version) {
        record(activity, "download_requested; versionCode=" + version);
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new Exception("Нет доступной папки для загрузки");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Не удалось создать папку загрузки");
            String name = "mk15-toolkit-" + version + ".apk";
            File target = new File(dir, name);
            if (target.exists()) target.delete();

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle("MK15 Toolkit " + version);
            request.setDescription("Загрузка обновления анализатора");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, name);
            long id = dm.enqueue(request);

            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_ID, id).putString(KEY_HASH, expectedHash)
                .putString(KEY_FILE, target.getAbsolutePath())
                .putInt(KEY_TARGET_VERSION, version).apply();

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())
                        && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == id) {
                        try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
                        finishDownload(activity, id);
                    }
                }
            };
            ContextCompat.registerReceiver(activity, receiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_NOT_EXPORTED);
            record(activity, "download_enqueued; id=" + id + "; versionCode=" + version);
            Toast.makeText(activity, "Загрузка началась. Дождись уведомления.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            record(activity, "download_start_failed; " + e.getClass().getSimpleName() + ": " + e.getMessage());
            new AlertDialog.Builder(activity).setTitle("Ошибка загрузки")
                .setMessage(e.getMessage()).setPositiveButton("ОК", null).show();
        }
    }

    private static void finishDownload(Activity activity, long id) {
        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
            android.database.Cursor cursor = dm.query(q);
            if (cursor == null) throw new Exception("Не удалось проверить загрузку");
            try {
                if (!cursor.moveToFirst()) throw new Exception("Загрузка не найдена");
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();
                    record(activity, "download_manager_failed; status=" + status + "; reason=" + reason);
                    throw new Exception("DownloadManager status=" + status + ", reason=" + reason);
                }
            } finally { cursor.close(); }

            android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String path = prefs.getString(KEY_FILE, "");
            String expected = prefs.getString(KEY_HASH, "");
            File file = new File(path);
            if (!file.isFile() || file.length() == 0) throw new Exception("APK-файл пуст или отсутствует");
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(expected)) {
                record(activity, "download_integrity_failed; expected_sha256_mismatch");
                file.delete();
                throw new Exception("SHA-256 не совпал. APK удалён из соображений безопасности.");
            }
            record(activity, "download_complete_and_hash_verified; bytes=" + file.length());
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();
            new AlertDialog.Builder(activity).setTitle("Обновление скачано")
                .setMessage("Контрольная сумма совпала. Android проверит подпись приложения при установке.")
                .setPositiveButton("Установить", (d, w) -> install(activity, file))
                .setNegativeButton("Позже", null).show();
        } catch (Exception e) {
            record(activity, "download_or_verification_failed; " + e.getClass().getSimpleName() + ": " + e.getMessage());
            new AlertDialog.Builder(activity).setTitle("Не удалось подготовить обновление")
                .setMessage(e.getMessage()).setPositiveButton("ОК", null).show();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) digest.update(b, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte x : digest.digest()) out.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
        return out.toString();
    }

    private static void install(Activity activity, File file) {
        try {
            record(activity, "install_requested");
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                record(activity, "install_permission_missing");
                new AlertDialog.Builder(activity)
                    .setTitle("Разрешение на установку")
                    .setMessage("Разреши установку APK для MK15 Toolkit в настройках Android, затем вернись в приложение и нажми «Установить» ещё раз.")
                    .setPositiveButton("Открыть настройки", (d, w) -> {
                        Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    }).setNegativeButton("Отмена", null).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(intent);
            record(activity, "android_package_installer_opened");
        } catch (Exception e) {
            record(activity, "installer_launch_failed; " + e.getClass().getSimpleName() + ": " + e.getMessage());
            new AlertDialog.Builder(activity).setTitle("Установка не запущена")
                .setMessage(e.getMessage()).setPositiveButton("ОК", null).show();
        }
    }
}
