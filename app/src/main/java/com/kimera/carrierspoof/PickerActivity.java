package com.kimera.carrierspoof;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PickerActivity extends Activity {

    private static final class Carrier {
        String name, alpha;
        final List<String> plmn = new ArrayList<>();
    }
    private static final class Country {
        String code, name;
        final List<Carrier> carriers = new ArrayList<>();
    }

    private List<Country> countries = new ArrayList<>();
    private Spinner countrySpin, carrierSpin, plmnSpin;
    private EditText lineEdit;
    private Button sim1Toggle, applyBtn;
    private TextView status, testView;
    private SharedPreferences sp;
    private boolean suppress = false;
    private boolean sim1On = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("carrier", MODE_PRIVATE);
        countries = loadCountries();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16); root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("CarrierSpoof by KiMeRa");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        countrySpin = new Spinner(this);
        List<String> countryNames = new ArrayList<>();
        for (Country c : countries) countryNames.add(c.name);
        countrySpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, countryNames));
        countrySpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (suppress) return;
                populateCarriers(pos);
                markDirty();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(countrySpin);

        sim1Toggle = new Button(this);
        sim1Toggle.setAllCaps(false);
        sim1Toggle.setOnClickListener(v -> { sim1On = !sim1On; refreshSim1Toggle(); markDirty(); });
        root.addView(sim1Toggle, fullWidth());

        carrierSpin = new Spinner(this);
        carrierSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                if (suppress) return;
                populatePlmn(pos, true);
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
        randomLineBtn.setOnClickListener(v -> { lineEdit.setText(randomLine(currentCountryCode())); markDirty(); });
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

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        Button clearBtn = new Button(this); clearBtn.setText("Clear");
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

    private void firstRun() {
        int ci = indexOfCountry("US");
        int xi = indexOfCarrier(ci, "Verizon");
        Carrier vzw = countries.get(ci).carriers.get(xi);
        int pi = new Random().nextInt(vzw.plmn.size());
        writeConfig(ci, xi, pi, randomLine("US"), true);
    }

    private void reloadFromConfig() {
        suppress = true;
        sim1On = !"off".equals(sp.getString("sim1", "on"));
        refreshSim1Toggle();
        int ci = indexOfCountry(sp.getString("country", "us").toUpperCase(Locale.US));
        int xi = indexOfCarrier(ci, sp.getString("name", "Verizon"));
        String numeric = sp.getString("numeric", "310004");
        int pi = indexOfPlmn(ci, xi, numeric);
        if (pi < 0) pi = 0;

        countrySpin.setSelection(ci);
        populateCarriers(ci);
        carrierSpin.setSelection(xi);
        populatePlmn(xi, false);
        plmnSpin.setSelection(pi);
        lineEdit.setText(sp.getString("line", ""));
        suppress = false;
        markDirty();
    }

    private void populateCarriers(int ci) {
        List<String> names = new ArrayList<>();
        for (Carrier c : countries.get(ci).carriers) names.add(c.name);
        carrierSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names));
    }

    private void populatePlmn(int xi, boolean randomize) {
        int ci = countrySpin.getSelectedItemPosition();
        if (ci < 0 || xi < 0 || xi >= countries.get(ci).carriers.size()) return;
        List<String> opts = new ArrayList<>();
        for (String s : countries.get(ci).carriers.get(xi).plmn) opts.add(s);
        plmnSpin.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opts));
        if (randomize && !opts.isEmpty()) plmnSpin.setSelection(new Random().nextInt(opts.size()));
    }

    private void applyGui() {
        int ci = countrySpin.getSelectedItemPosition();
        int xi = carrierSpin.getSelectedItemPosition();
        int pi = plmnSpin.getSelectedItemPosition();
        if (ci < 0 || xi < 0 || pi < 0) return;
        String line = lineEdit.getText().toString().trim();
        writeConfig(ci, xi, pi, line, sim1On);
        markDirty();
        status.setText("Saved. Now reboot your device to see the carrier info changed!");
    }

    private void writeConfig(int ci, int xi, int pi, String line, boolean sim1) {
        Country co = countries.get(ci);
        Carrier ca = co.carriers.get(xi);
        String numeric = ca.plmn.get(pi).replace("-", "");

        String existingLine = sp.getString("line", null);
        if (line == null || line.isEmpty()) {
            line = (existingLine != null) ? existingLine : randomLine(co.code);
        }

        Random r = new Random();
        int msinLen = 15 - numeric.length();
        String imsi = numeric + String.format(Locale.US, "%0" + msinLen + "d",
                (long) (r.nextDouble() * Math.pow(10, msinLen)));
        String iccidBase = "891" + numeric;
        int pad = Math.max(10, 19 - iccidBase.length());
        String iccid = iccidBase + String.format(Locale.US, "%0" + pad + "d",
                (long) (r.nextDouble() * Math.pow(10, pad)));

        sp.edit()
                .putString("name", ca.name)
                .putString("numeric", numeric)
                .putString("alpha", ca.alpha)
                .putString("country", co.code.toLowerCase(Locale.US))
                .putString("imsi", imsi)
                .putString("iccid", iccid)
                .putString("line", line)
                .putString("sim1", sim1 ? "on" : "off")
                .commit();
        chmodPrefs();
    }

    private void markDirty() {
        if (suppress) return;
        boolean dirty = isDirty();
        applyBtn.setVisibility(dirty ? View.VISIBLE : View.GONE);
    }

    private boolean isDirty() {
        int ci = countrySpin.getSelectedItemPosition();
        int xi = carrierSpin.getSelectedItemPosition();
        int pi = plmnSpin.getSelectedItemPosition();
        if (ci < 0 || xi < 0 || pi < 0) return false;
        String guiNumeric = countries.get(ci).carriers.get(xi).plmn.get(pi).replace("-", "");
        String guiName = countries.get(ci).carriers.get(xi).name;
        String guiCountry = countries.get(ci).code.toLowerCase(Locale.US);
        String guiLine = lineEdit.getText().toString().trim();
        String guiSim1 = sim1On ? "on" : "off";

        return !guiNumeric.equals(sp.getString("numeric", ""))
                || !guiName.equals(sp.getString("name", ""))
                || !guiCountry.equals(sp.getString("country", ""))
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
        String country = sp.getString("country", "");
        String line = sp.getString("line", "");
        StringBuilder sb = new StringBuilder();

        safeAppend(sb, "SIM operator", () -> tm.getSimOperator(), numeric);
        safeAppend(sb, "SIM name", () -> tm.getSimOperatorName(), alpha);
        safeAppend(sb, "SIM country", () -> tm.getSimCountryIso(), country);
        safeAppend(sb, "Net operator", () -> tm.getNetworkOperator(), numeric);
        safeAppend(sb, "Net name", () -> tm.getNetworkOperatorName(), alpha);
        safeAppend(sb, "Net country", () -> tm.getNetworkCountryIso(), country);

        try {
            int st = tm.getSimState();
            boolean ok = (st == TelephonyManager.SIM_STATE_READY) == sim1On;
            sb.append(ok ? "\u2705" : "\u274C").append(" SIM state: ").append(simStateName(st)).append('\n');
        } catch (Throwable t) { sb.append("SIM state: n/a\n"); }

        safeAppend(sb, "Line", () -> tm.getLine1Number(), line);
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
            sb.append(label).append(": n/a\n");
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

    private String currentCountryCode() {
        int ci = countrySpin.getSelectedItemPosition();
        return (ci >= 0) ? countries.get(ci).code : "US";
    }

    private String randomLine(String countryCode) {
        Random r = new Random();
        if ("SY".equalsIgnoreCase(countryCode)) {
            int op = 3 + r.nextInt(6);
            return String.format(Locale.US, "+9639%d%07d", op, r.nextInt(10000000));
        }
        int npa, nxx;
        do { npa = 200 + r.nextInt(800); } while (npa % 100 == 11);
        do { nxx = 200 + r.nextInt(800); } while (nxx % 100 == 11);
        return String.format(Locale.US, "+1%03d%03d%04d", npa, nxx, r.nextInt(10000));
    }

    private int indexOfCountry(String code) {
        for (int i = 0; i < countries.size(); i++)
            if (countries.get(i).code.equalsIgnoreCase(code)) return i;
        return 0;
    }
    private int indexOfCarrier(int ci, String name) {
        List<Carrier> cs = countries.get(ci).carriers;
        for (int i = 0; i < cs.size(); i++) if (cs.get(i).name.equals(name)) return i;
        return 0;
    }
    private int indexOfPlmn(int ci, int xi, String numeric) {
        List<String> pl = countries.get(ci).carriers.get(xi).plmn;
        for (int i = 0; i < pl.size(); i++)
            if (pl.get(i).replace("-", "").equals(numeric)) return i;
        return -1;
    }

    private void chmodPrefs() {
        try {
            File dd = new File(getApplicationInfo().dataDir);
            dd.setExecutable(true, false);
            File dir = new File(dd, "shared_prefs");
            dir.setReadable(true, false); dir.setExecutable(true, false);
            new File(dir, "carrier.xml").setReadable(true, false);
        } catch (Throwable ignored) { }
        try {
            Process su = Runtime.getRuntime().exec("su");
            su.getOutputStream().write(("cat " + getApplicationInfo().dataDir
                    + "/shared_prefs/carrier.xml > /data/system/carrierspoof.conf\n"
                    + "chmod 644 /data/system/carrierspoof.conf\n").getBytes());
            su.getOutputStream().flush();
            su.waitFor();
        } catch (Throwable ignored) { }
    }

    private List<Country> loadCountries() {
        try {
            String json = readAsset("carriers.json");
            JSONArray arr = new JSONObject(json).getJSONArray("countries");
            List<Country> cs = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject co = arr.getJSONObject(i);
                Country c = new Country();
                c.code = co.getString("code");
                c.name = co.getString("name");
                JSONArray carriers = co.getJSONArray("carriers");
                for (int j = 0; j < carriers.length(); j++) {
                    JSONObject ca = carriers.getJSONObject(j);
                    Carrier car = new Carrier();
                    car.name = ca.getString("name");
                    car.alpha = ca.getString("alpha");
                    JSONArray pl = ca.getJSONArray("plmn");
                    for (int k = 0; k < pl.length(); k++) car.plmn.add(pl.getString(k));
                    c.carriers.add(car);
                }
                cs.add(c);
            }
            if (!cs.isEmpty()) return cs;
        } catch (Throwable t) { /* fall through */ }

        Country us = new Country(); us.code = "US"; us.name = "United States";
        Carrier vzw = new Carrier(); vzw.name = "Verizon"; vzw.alpha = "Verizon";
        vzw.plmn.add("310-004"); us.carriers.add(vzw);
        List<Country> cs = new ArrayList<>(); cs.add(us);
        return cs;
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
