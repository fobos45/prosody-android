package com.example.prosodyandroid;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProsodyManager {

    private static final String TAG = "ProsodyManager";
    private static final ProsodyManager INSTANCE = new ProsodyManager();

    private Process prosodyProcess;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG = 64 * 1024;

    public static ProsodyManager getInstance() { return INSTANCE; }

    public boolean isRunning() { return running.get(); }

    public String getLog() {
        synchronized (logBuffer) { return logBuffer.toString(); }
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    public void start(Context ctx, String hostname) {
        if (running.get()) return;
        new Thread(() -> startInternal(ctx, hostname), "ProsodyStart").start();
    }

    private void startInternal(Context ctx, String hostname) {
        try {
            File baseDir = new File(ctx.getFilesDir(), "prosody");
            extractAssets(ctx, baseDir);
            writeConfig(ctx, baseDir, hostname);

            File luaBin  = new File(baseDir, "bin/lua");
            File entry   = new File(baseDir, "share/prosody/prosody");
            File cfgFile = new File(baseDir, "cfg/prosody.cfg.lua");
            File dataDir = new File(baseDir, "data");
            dataDir.mkdirs();

            // LUA_PATH and LUA_CPATH
            String luaPath  = baseDir + "/share/prosody/?.lua;"
                            + baseDir + "/share/prosody/?/init.lua;"
                            + baseDir + "/share/?.lua";
            String luaCPath = baseDir + "/lib/?.so";

            ProcessBuilder pb = new ProcessBuilder(
                luaBin.getAbsolutePath(),
                entry.getAbsolutePath(),
                "--config", cfgFile.getAbsolutePath()
            );
            pb.environment().put("LUA_PATH", luaPath);
            pb.environment().put("LUA_CPATH", luaCPath);
            pb.environment().put("HOME", baseDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.directory(baseDir);

            prosodyProcess = pb.start();
            running.set(true);
            log("Prosody started on " + hostname + ":5222\n");

            // Read stdout/stderr
            byte[] buf = new byte[4096];
            InputStream is = prosodyProcess.getInputStream();
            int n;
            while ((n = is.read(buf)) != -1) {
                log(new String(buf, 0, n));
            }

            prosodyProcess.waitFor();
            running.set(false);
            log("\nProsody stopped.\n");

        } catch (Exception e) {
            running.set(false);
            log("\nError: " + e.getMessage() + "\n");
            Log.e(TAG, "Failed to start Prosody", e);
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    public void stop() {
        if (prosodyProcess != null) {
            prosodyProcess.destroy();
            prosodyProcess = null;
        }
        running.set(false);
    }

    // ── Config generation ─────────────────────────────────────────────────────

    private void writeConfig(Context ctx, File baseDir, String hostname) throws Exception {
        File cfgDir = new File(baseDir, "cfg");
        cfgDir.mkdirs();
        File cfg = new File(cfgDir, "prosody.cfg.lua");
        File dataDir = new File(baseDir, "data");
        dataDir.mkdirs();

        String config =
            "-- Auto-generated Prosody config\n" +
            "daemonize = false\n" +
            "pidfile = \"" + baseDir + "/prosody.pid\"\n" +
            "data_path = \"" + dataDir.getAbsolutePath() + "\"\n" +
            "log = { { to = \"stdout\", levels = { min = \"info\" } } }\n\n" +
            "modules_enabled = {\n" +
            "  \"roster\"; \"saslauth\"; \"disco\"; \"private\";\n" +
            "  \"vcard_legacy\"; \"version\"; \"uptime\"; \"time\";\n" +
            "  \"ping\"; \"register\"; \"admin_adhoc\";\n" +
            "}\n\n" +
            "allow_registration = true\n" +
            "c2s_require_encryption = false\n" +
            "s2s_require_encryption = false\n" +
            "authentication = \"internal_plain\"\n\n" +
            "VirtualHost \"" + hostname + "\"\n" +
            "  c2s_ports = { 5222 }\n";

        FileWriter fw = new FileWriter(cfg);
        fw.write(config);
        fw.close();
        Log.i(TAG, "Config written: " + cfg.getAbsolutePath());
    }

    // ── Asset extraction ──────────────────────────────────────────────────────

    private void extractAssets(Context ctx, File baseDir) throws Exception {
        // Extract native binaries: assets/native/arm64-v8a/ → baseDir/bin/ and baseDir/lib/
        extractDir(ctx, "native/arm64-v8a", baseDir, true);
        // Extract Prosody Lua files: assets/prosody/ → baseDir/share/prosody/
        extractDir(ctx, "prosody", new File(baseDir, "share/prosody"), false);
        Log.i(TAG, "Assets extracted to " + baseDir);
    }

    private void extractDir(Context ctx, String assetDir, File outDir, boolean native_) throws Exception {
        outDir.mkdirs();
        String[] files = ctx.getAssets().list(assetDir);
        if (files == null) return;
        for (String name : files) {
            String assetPath = assetDir + "/" + name;
            String[] sub = ctx.getAssets().list(assetPath);
            if (sub != null && sub.length > 0) {
                extractDir(ctx, assetPath, new File(outDir, name), native_);
            } else {
                File out = native_
                    ? new File(outDir.getParentFile(), isSo(name) ? "lib/" + name : "bin/" + name)
                    : new File(outDir, name);
                out.getParentFile().mkdirs();
                if (!out.exists()) {
                    copyAsset(ctx, assetPath, out);
                    if (native_ && !isSo(name)) out.setExecutable(true);
                }
            }
        }
    }

    private boolean isSo(String name) { return name.endsWith(".so"); }

    private void copyAsset(Context ctx, String assetPath, File out) throws Exception {
        try (InputStream in = ctx.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    // ── Log ──────────────────────────────────────────────────────────────────

    private void log(String msg) {
        synchronized (logBuffer) {
            logBuffer.append(msg);
            if (logBuffer.length() > MAX_LOG)
                logBuffer.delete(0, logBuffer.length() - MAX_LOG);
        }
    }
}
