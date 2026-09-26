package com.kimera.carrierspoof;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PickerActivity extends Activity {

    private static final class Carrier {
        String name, alpha, spn, iccid, apn;
        final List<String> plmn = new ArrayList<>();
    }
    private static final class Country {
        String code, name;
        final List<Carrier> carriers = new ArrayList<>();
    }

    private Country us;
    private Spinner carrierSpin, plmnSpin;
    private EditText lineEdit;
    private Button sim1Toggle, clearBtn, applyBtn;
    private TextView status, testView;
    private SharedPreferences sp;
    private boolean suppress = false;
    private boolean sim1On = true;
    private int lastCarrier = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("carrier", MODE_PRIVATE);
        if (checkSelfPermission("android.permission.READ_PHONE_STATE")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.READ_PHONE_STATE"}, 100);
        }
        us = loadUsCountry();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("CarrierSpoof v" + versionName());
        title.setTextSize(20);
        root.addView(title);

        TextView byline = new TextView(this);
        byline.setText("by KiMeRa");
        byline.setTextSize(12);
        byline.setPadding(0, 0, 0, dp(12));
        root.addView(byline);

        TextView countryLabel = new TextView(this);
        countryLabel.setText(us.name);   // "🇺🇸 USA"
        countryLabel.setTextSize(16);
        countryLabel.setPadding(0, 0, 0, dp(4));
        root.addView(countryLabel);

        sim1Toggle = new Button(this);
        sim1Toggle.setAllCaps(false);
        sim1Toggle.setOnClickListener(v -> { sim1On = !sim1On; refreshSim1Toggle(); markDirty(); });
        root.addView(sim1Toggle, fullWidth());

        carrierSpin = new Spinner(this);
        carrierSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (suppress) { lastCarrier = pos; return; }
                if (pos != lastCarrier) {
                    populatePlmn(pos, true);   // randomize ONLY when carrier changes
                }
                lastCarrier = pos;
                markDirty();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(carrierSpin);

        plmnSpin = new Spinner(this);
        plmnSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (suppress) return;
                markDirty();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(plmnSpin);

        lineEdit = new EditText(this);
        lineEdit.setHint("Phone number (optional)");
        lineEdit.setSingleLine(true);
        lineEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { if (!suppress) markDirty(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        root.addView(lineEdit, fullWidth());

        LinearLayout lineBtns = new LinearLayout(this);
        lineBtns.setOrientation(LinearLayout.HORIZONTAL);
        Button randomLineBtn = new Button(this); randomLineBtn.setText("Random #");
        Button emptyLineBtn = new Button(this); emptyLineBtn.setText("Empty #");
        randomLineBtn.setOnClickListener(v -> { lineEdit.setText(usRandomLine()); markDirty(); });
        emptyLineBtn.setOnClickListener(v -> { lineEdit.setText(""); markDirty(); });
        lineBtns.addView(randomLineBtn, weight1());
        lineBtns.addView(emptyLineBtn, weight1());
        root.addView(lineBtns);

        Button testBtn = new Button(this);
        testBtn.setText("Test system SIM info");
        testBtn.setOnClickListener(v -> runTest());
        root.addView(testBtn, fullWidth());

        testView = new TextView(this);
        testView.setTextSize(12);
        testView.setTypeface(android.graphics.Typeface.MONOSPACE);
        testView.setPadding(0, dp(8), 0, 0);
        root.addView(testView);

        Button debugBtn = new Button(this);
        debugBtn.setText("Save debug log");
        debugBtn.setOnClickListener(v -> saveDebugLog());
        root.addView(debugBtn, fullWidth());

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        clearBtn = new Button(this); clearBtn.setText("Clear");
        applyBtn = new Button(this); applyBtn.setText("Apply");
        clearBtn.setOnClickListener(v -> { reloadFromConfig(); status.setText(""); });
        applyBtn.setOnClickListener(v -> applyGui());
        bottom.addView(clearBtn, weight1());
        bottom.addView(applyBtn, weight1());
        root.addView(bottom);

        setContentView(root);

        if (sp.getString("numeric", null) == null) firstRun();
        reloadFromConfig();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private void firstRun() {
        int xi = indexOfCarrier("Verizon");
        Carrier vzw = us.carriers.get(xi);
        int pi = new Random().nextInt(vzw.plmn.size());
        writePrefs(xi, pi, usRandomLine(), true);
        stageAsync(vzw.plmn.get(pi).replace("-", ""), vzw.apn, vzw.name);
    }

    private void reloadFromConfig() {
        int xi = indexOfCarrier(sp.getString("name", "Verizon"));
        int pi = indexOfPlmn(xi, sp.getString("numeric", "310004"));
        if (pi < 0) pi = 0;
        String line = sp.getString("line", "");
        sim1On = !"off".equals(sp.getString("sim1", "on"));

        suppress = true;
        populateCarriers();
        carrierSpin.setSelection(xi);
        populatePlmn(xi, false);
        plmnSpin.setSelection(pi);
        lineEdit.setText(line);
        refreshSim1Toggle();
        lastCarrier = xi;
        suppress = false;

        final int fxi = xi, fpi = pi;
        carrierSpin.post(() -> {
            suppress = true;
            carrierSpin.setSelection(fxi);
            plmnSpin.setSelection(fpi);
            lastCarrier = fxi;
            suppress = false;
            markDirty();
        });
    }

    private void populateCarriers() {
        List<String> names = new ArrayList<>();
        for (Carrier c : us.carriers) names.add(c.name);
        carrierSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names));
    }

    private void populatePlmn(int xi, boolean randomize) {
        if (xi < 0 || xi >= us.carriers.size()) return;
        List<String> opts = new ArrayList<>();
        for (String s : us.carriers.get(xi).plmn) opts.add(s);
        plmnSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opts));
        if (randomize && !opts.isEmpty()) plmnSpin.setSelection(new Random().nextInt(opts.size()));
    }

    /** Apply: only the fast SharedPreferences write happens on the UI thread.
     *  All root/su work (config staging + APN insert) runs on a background
     *  thread with a hard timeout, so Apply can never freeze the UI. */
    private void applyGui() {
        int xi = carrierSpin.getSelectedItemPosition();
        int pi = plmnSpin.getSelectedItemPosition();
        if (xi < 0 || pi < 0) return;
        Carrier ca = us.carriers.get(xi);
        String numeric = ca.plmn.get(pi).replace("-", "");
        String line = lineEdit.getText().toString().trim();
        writePrefs(xi, pi, line, sim1On);
        markDirty();
        status.setText("Saved — now reboot to see the carrier info changed.\n"
                + "(Staging config + APN in the background; APN insert needs root.)");
        stageAsync(numeric, ca.apn, ca.name);
    }

    private void stageAsync(final String numeric, final String apn, final String name) {
        new Thread(() -> {
            suOut("cat " + getApplicationInfo().dataDir
                    + "/shared_prefs/carrier.xml > /data/system/carrierspoof.conf"
                    + "; chmod 644 /data/system/carrierspoof.conf");
            insertApn(numeric, apn != null ? apn : "internet", name);
        }).start();
    }

    private void writePrefs(int xi, int pi, String line, boolean sim1) {
        Carrier ca = us.carriers.get(xi);
        String numeric = ca.plmn.get(pi).replace("-", "");

        String existingLine = sp.getString("line", null);
        if (line == null || line.isEmpty()) {
            line = (existingLine != null) ? existingLine : usRandomLine();
        }

        Random r = new Random();
        int msinLen = 15 - numeric.length();
        String imsi = numeric + String.format(Locale.US, "%0" + msinLen + "d",
                (long) (r.nextDouble() * Math.pow(10, msinLen)));
        String iccidBase = (ca.iccid != null && !ca.iccid.isEmpty()) ? ca.iccid : ("891" + numeric);
        int pad = Math.max(11, 19 - iccidBase.length());
        String iccid = iccidBase + String.format(Locale.US, "%0" + pad + "d",
                (long) (r.nextDouble() * Math.pow(10, pad)));

        sp.edit()
                .putString("name", ca.name)
                .putString("numeric", numeric)
                .putString("alpha", ca.alpha)
                .putString("spn", ca.spn)
                .putString("country", us.code.toLowerCase(Locale.US))
                .putString("imsi", imsi)
                .putString("iccid", iccid)
                .putString("line", line)
                .putString("sim1", sim1 ? "on" : "off")
                .putString("apn", ca.apn != null ? ca.apn : "internet")
                .commit();
        chmodPrefs();
    }

    private void markDirty() {
        if (suppress) return;
        int vis = isDirty() ? View.VISIBLE : View.GONE;
        applyBtn.setVisibility(vis);
        clearBtn.setVisibility(vis);
    }

    private boolean isDirty() {
        int xi = carrierSpin.getSelectedItemPosition();
        int pi = plmnSpin.getSelectedItemPosition();
        if (xi < 0 || pi < 0) return false;
        String guiNumeric = us.carriers.get(xi).plmn.get(pi).replace("-", "");
        String guiName = us.carriers.get(xi).name;
        String guiLine = lineEdit.getText().toString().trim();
        String guiSim1 = sim1On ? "on" : "off";

        return !guiNumeric.equals(sp.getString("numeric", ""))
                || !guiName.equals(sp.getString("name", ""))
                || !guiLine.equals(sp.getString("line", ""))
                || !guiSim1.equals(sp.getString("sim1", "on"));
    }

    private void refreshSim1Toggle() {
        sim1Toggle.setText("SIM1: " + (sim1On ? "ON" : "OFF"));
        sim1Toggle.setTextColor(sim1On ? 0xFF2E7D32 : 0xFFC62828);
        carrierSpin.setEnabled(sim1On);
        plmnSpin.setEnabled(sim1On);
        lineEdit.setEnabled(sim1On);
    }

    private void runTest() {
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        String numeric = sp.getString("numeric", "");
        String alpha = sp.getString("alpha", "");
        String spn = sp.getString("spn", "");
        String line = sp.getString("line", "");
        StringBuilder sb = new StringBuilder();

        safeAppend(sb, "SIM operator", () -> tm.getSimOperator(), numeric);
        safeAppend(sb, "SIM name", () -> tm.getSimOperatorName(), spn);
        safeAppend(sb, "Net operator", () -> tm.getNetworkOperator(), numeric);
        safeAppend(sb, "Net name", () -> tm.getNetworkOperatorName(), alpha);

        try {
            int st = tm.getSimState();
            boolean ok = (st == TelephonyManager.SIM_STATE_READY) == sim1On;
            sb.append(ok ? "\u2705" : "\u274C").append(" SIM state: ").append(simStateName(st)).append('\n');
        } catch (Throwable t) { sb.append("SIM state: n/a\n"); }

        safeAppend(sb, "Line", () -> tm.getLine1Number(), line);
        safeAppend(sb, "IMSI", () -> tm.getSubscriberId(), sp.getString("imsi", ""));
        safeAppend(sb, "ICCID", () -> tm.getSimSerialNumber(), sp.getString("iccid", ""));
        testView.setText(sb.toString().trim());
    }

    private interface Get { String get() throws Exception; }
    private void safeAppend(StringBuilder sb, String label, Get g, String expected) {
        try {
            String actual = g.get();
            boolean ok = actual != null && actual.equals(expected);
            sb.append(ok ? "\u2705" : "\u274C").append(' ')
              .append(label).append(": ")
              .append(actual == null || actual.isEmpty() ? "(empty)" : actual).append('\n');
        } catch (Throwable t) {
            sb.append("\u274C").append(' ').append(label).append(": n/a\n");
        }
    }

    private String simStateName(int st) {
        switch (st) {
            case TelephonyManager.SIM_STATE_ABSENT: return "ABSENT";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "PIN_REQUIRED";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "PUK_REQUIRED";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "NETWORK_LOCKED";
            case TelephonyManager.SIM_STATE_READY: return "READY";
            case TelephonyManager.SIM_STATE_NOT_READY: return "NOT_READY";
            case TelephonyManager.SIM_STATE_PERM_DISABLED: return "PERM_DISABLED";
            case TelephonyManager.SIM_STATE_CARD_IO_ERROR: return "CARD_IO_ERROR";
            default: return "STATE_" + st;
        }
    }

    // ---------- debug log ----------

    private void saveDebugLog() {
        status.setText("Collecting debug log…");
        new Thread(() -> {
            final String text = buildDebugText();
            runOnUiThread(() -> {
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, "carrierspoof_debug.txt");
                    cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(text.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    status.setText("Saved to Downloads/carrierspoof_debug.txt\n"
                            + "Share that file to debug IMSI/ICCID/APN.");
                } catch (Throwable t) {
                    status.setText("Debug log failed: " + t);
                }
            });
        }).start();
    }

    private String buildDebugText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== CarrierSpoof v").append(versionName()).append(" debug log ===\n");
        sb.append("Time: ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
          .append('\n');
        sb.append("Device: ").append(android.os.Build.MANUFACTURER).append(' ')
          .append(android.os.Build.MODEL).append(", Android ")
          .append(android.os.Build.VERSION.RELEASE).append(" (SDK ")
          .append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

        sb.append("--- Saved config ---\n");
        for (String k : new String[]{"name", "numeric", "alpha", "spn", "apn", "imsi", "iccid", "line", "sim1"})
            sb.append(k).append('=').append(sp.getString(k, "(unset)")).append('\n');
        sb.append('\n');

        sb.append("--- Live system values ---\n");
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        appendSys(sb, "simOperator", () -> tm.getSimOperator());
        appendSys(sb, "simOperatorName", () -> tm.getSimOperatorName());
        appendSys(sb, "simCountryIso", () -> tm.getSimCountryIso());
        appendSys(sb, "networkOperator", () -> tm.getNetworkOperator());
        appendSys(sb, "networkOperatorName", () -> tm.getNetworkOperatorName());
        appendSys(sb, "networkCountryIso", () -> tm.getNetworkCountryIso());
        appendSys(sb, "simState", () -> String.valueOf(tm.getSimState()));
        appendSys(sb, "line1Number", () -> tm.getLine1Number());
        appendSys(sb, "subscriberId(IMSI)", () -> tm.getSubscriberId());
        appendSys(sb, "simSerialNumber(ICCID)", () -> tm.getSimSerialNumber());
        sb.append('\n');

        sb.append("--- Relevant system properties (via su getprop) ---\n");
        for (String line : suOut("getprop").split("\n")) {
            String t = line.trim();
            if (t.startsWith("[gsm.") || t.startsWith("[ril.")
                    || t.startsWith("[persist.radio") || t.startsWith("[persist.ril")
                    || t.startsWith("[ro.multisim") || t.startsWith("[ro.vendor.multisim"))
                sb.append(t).append('\n');
        }
        sb.append('\n');

        sb.append("--- Module logcat (LSPosed-Bridge) ---\n");
        sb.append(suOut("logcat -d -s LSPosed-Bridge:V"));
        sb.append("\n(hint: if no 'phone process' or 'system_server' install lines appear above, "
                + "the module is not injected into those processes — check the module scope in "
                + "LSPosed/Vector. If 'PhoneSubInfoController: N methods hooked' is missing, "
                + "IMSI/ICCID cannot be spoofed.)\n");
        return sb.toString();
    }

    private void appendSys(StringBuilder sb, String label, Get g) {
        try {
            String v = g.get();
            sb.append(label).append(" = ").append(v == null ? "(null)" : v).append('\n');
        } catch (Throwable t) {
            sb.append(label).append(" = EXCEPTION ").append(t.getClass().getName())
              .append(": ").append(t.getMessage()).append('\n');
        }
    }

    // ---------- su helpers (all bounded, never block the UI) ----------

    /** Runs a command as root, bounded to 6 s. Returns stdout ("" on failure/timeout). */
    private String suOut(final String cmd) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Process[] proc = new Process[1];
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Process p = Runtime.getRuntime().exec("su");
                    proc[0] = p;
                    p.getOutputStream().write((cmd + "\nexit\n").getBytes());
                    p.getOutputStream().flush();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = p.getInputStream().read(buf)) != -1) out.write(buf, 0, n);
                    p.waitFor();
                } catch (Throwable ignored) { }
            }
        });
        t.setDaemon(true);
        t.start();
        try { t.join(6000L); } catch (InterruptedException ignored) { }
        if (t.isAlive()) {
            try { if (proc[0] != null) proc[0].destroy(); } catch (Throwable ignored) { }
        }
        try { return out.toString("UTF-8"); } catch (Throwable t2) { return ""; }
    }

    /** Insert the carrier's APN into the telephony carriers DB so Settings shows it.
     *  Needs root (grant CarrierSpoof in KernelSU). Idempotent: deletes old rows first. */
    private void insertApn(String numeric, String apn, String name) {
        if (numeric == null || numeric.length() < 5) return;
        String mcc = numeric.substring(0, 3);
        String mnc = numeric.substring(3);
        suOut("content delete --uri content://telephony/carriers"
                + " --where \"numeric='" + numeric + "'\"");
        suOut("content insert --uri content://telephony/carriers"
                + " --bind name:s:'" + name + " Internet'"
                + " --bind apn:s:'" + apn + "'"
                + " --bind numeric:s:'" + numeric + "'"
                + " --bind mcc:s:'" + mcc + "'"
                + " --bind mnc:s:'" + mnc + "'"
                + " --bind type:s:'default,mms,supl'");
    }

    private String usRandomLine() {
        Random r = new Random();
        int npa, nxx;
        do { npa = 200 + r.nextInt(800); } while (npa % 100 == 11);
        do { nxx = 200 + r.nextInt(800); } while (nxx % 100 == 11);
        return String.format(Locale.US, "+1%03d%03d%04d", npa, nxx, r.nextInt(10000));
    }

    private int indexOfCarrier(String name) {
        for (int i = 0; i < us.carriers.size(); i++)
            if (us.carriers.get(i).name.equals(name)) return i;
        return 0;
    }
    private int indexOfPlmn(int xi, String numeric) {
        List<String> pl = us.carriers.get(xi).plmn;
        for (int i = 0; i < pl.size(); i++)
            if (pl.get(i).replace("-", "").equals(numeric)) return i;
        return -1;
    }

    /** Fast, no root: make prefs dir/file readable so the hooked processes can read them. */
    private void chmodPrefs() {
        try {
            File dd = new File(getApplicationInfo().dataDir);
            dd.setExecutable(true, false);
            File dir = new File(dd, "shared_prefs");
            dir.setReadable(true, false); dir.setExecutable(true, false);
            new File(dir, "carrier.xml").setReadable(true, false);
        } catch (Throwable ignored) { }
    }

    private Country loadUsCountry() {
        try {
            String json = readAsset("carriers.json");
            JSONArray arr = new JSONObject(json).getJSONArray("countries");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject co = arr.getJSONObject(i);
                if (!"US".equalsIgnoreCase(co.optString("code"))) continue;
                Country c = new Country();
                c.code = co.getString("code");
                c.name = co.getString("name");
                JSONArray carriers = co.getJSONArray("carriers");
                for (int j = 0; j < carriers.length(); j++) {
                    JSONObject ca = carriers.getJSONObject(j);
                    Carrier car = new Carrier();
                    car.name = ca.getString("name");
                    car.alpha = ca.optString("alpha", car.name);
                    car.spn = ca.optString("spn", car.alpha);
                    car.iccid = ca.optString("iccid", "891480");
                    car.apn = ca.optString("apn", "internet");
                    JSONArray pl = ca.getJSONArray("plmn");
                    for (int k = 0; k < pl.length(); k++) car.plmn.add(pl.getString(k));
                    c.carriers.add(car);
                }
                return c;
            }
        } catch (Throwable t) { /* fall through */ }

        Country us = new Country(); us.code = "US"; us.name = "\uD83C\uDDFA\uD83C\uDDF8 USA";
        Carrier vzw = new Carrier();
        vzw.name = "Verizon"; vzw.alpha = "Verizon Wireless"; vzw.spn = "Verizon";
        vzw.iccid = "891480"; vzw.apn = "vzwinternet";
        vzw.plmn.add("310-004"); us.carriers.add(vzw);
        return us;
    }

    private String readAsset(String name) throws Exception {
        InputStream in = getAssets().open(name);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private LinearLayout.LayoutParams weight1() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
