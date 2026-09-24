package com.kimera.carrierspoof;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
    private TextView current, status;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("carrier", MODE_PRIVATE);
        countries = loadCountries();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20); root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("CarrierSpoof by KiMeRa");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        current = new TextView(this);
        current.setTextSize(13);
        current.setPadding(0, 0, 0, dp(10));
        root.addView(current);

        countrySpin = new Spinner(this);
        carrierSpin = new Spinner(this);
        plmnSpin = new Spinner(this);

        List<String> countryNames = new ArrayList<>();
        for (Country c : countries) countryNames.add(c.name);
        countrySpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, countryNames));

        countrySpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View v, int pos, long id) {
                populateCarriers(pos, null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        carrierSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View v, int pos, long id) {
                populatePlmn(pos, null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        root.addView(countrySpin);
        root.addView(carrierSpin);
        root.addView(plmnSpin);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        Button apply = new Button(this); apply.setText("Apply");
        Button rand = new Button(this);  rand.setText("Random");

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, 0);

        apply.setOnClickListener(v -> onApply());
        rand.setOnClickListener(v -> onRandom());

        root.addView(apply, lp);
        root.addView(rand, lp);
        root.addView(status);
        setContentView(root);

        initSelection();
    }

    private void initSelection() {
        String savedNumeric = sp.getString("numeric", null);
        if (savedNumeric == null) {
            // first run: US + Verizon + random Verizon MCC/MNC + random US line
            int ci = indexOfCountry("US");
            int xi = indexOfCarrier(ci, "Verizon");
            Carrier verizon = countries.get(ci).carriers.get(xi);
            int pi = new Random().nextInt(verizon.plmn.size());
            save(ci, xi, pi, true);
            syncSpinners(ci, xi, pi);
        } else {
            int ci = indexOfCountry(sp.getString("country", "us").toUpperCase(Locale.US));
            int xi = indexOfCarrier(ci, sp.getString("name", "Verizon"));
            int pi = indexOfPlmn(ci, xi, savedNumeric);
            if (pi < 0) pi = 0;
            syncSpinners(ci, xi, pi);
        }
        refreshCurrent();
    }

    private void onApply() {
        int ci = countrySpin.getSelectedItemPosition();
        int xi = carrierSpin.getSelectedItemPosition();
        int pi = plmnSpin.getSelectedItemPosition();
        if (ci < 0 || xi < 0 || pi < 0) return;
        boolean countryChanged = !countries.get(ci).code.equalsIgnoreCase(
                sp.getString("country", "us"));
        save(ci, xi, pi, countryChanged);
        refreshCurrent();
        status.setText("Saved: " + countries.get(ci).carriers.get(xi).name
                + " (" + plmn(ci, xi, pi) + ")\n"
                + "Now reboot your device to see the carrier info changed!");
    }

    private void onRandom() {
        Random r = new Random();
        int ci = countrySpin.getSelectedItemPosition();
        if (ci < 0) ci = 0;
        Country co = countries.get(ci);
        int xi = r.nextInt(co.carriers.size());
        int pi = r.nextInt(co.carriers.get(xi).plmn.size());
        save(ci, xi, pi, true);
        syncSpinners(ci, xi, pi);
        refreshCurrent();
        status.setText("Random: " + co.carriers.get(xi).name + " (" + plmn(ci, xi, pi) + ")");
    }

    private void save(int ci, int xi, int pi, boolean regenLine) {
        Country co = countries.get(ci);
        Carrier ca = co.carriers.get(xi);
        String plmn = plmn(ci, xi, pi);
        String numeric = plmn.replace("-", "");

        String existingLine = sp.getString("line", null);
        String line = (regenLine || existingLine == null)
                ? randomLine(co.code) : existingLine;

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
                .commit();

        chmodPrefs();
    }

    private void syncSpinners(int ci, int xi, int pi) {
        countrySpin.setSelection(ci);
        populateCarriers(ci, xi);
        populatePlmn(xi, pi);
    }

    private void populateCarriers(int ci, Integer select) {
        List<String> names = new ArrayList<>();
        for (Carrier c : countries.get(ci).carriers) names.add(c.name);
        carrierSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names));
        if (select != null && select >= 0 && select < names.size()) carrierSpin.setSelection(select);
    }

    private void populatePlmn(int xi, Integer select) {
        int ci = countrySpin.getSelectedItemPosition();
        if (ci < 0 || xi < 0 || xi >= countries.get(ci).carriers.size()) return;
        List<String> opts = new ArrayList<>();
        for (String s : countries.get(ci).carriers.get(xi).plmn) opts.add(s);
        plmnSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts));
        if (select != null && select >= 0 && select < opts.size()) plmnSpin.setSelection(select);
    }

    private String plmn(int ci, int xi, int pi) {
        return countries.get(ci).carriers.get(xi).plmn.get(pi);
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

    private void refreshCurrent() {
        current.setText("Current: " + sp.getString("alpha", "Verizon")
                + " (" + sp.getString("numeric", "310004") + ") · "
                + sp.getString("line", "+12025550134"));
    }

    private void chmodPrefs() {
        try {
            File dd = new File(getApplicationInfo().dataDir);
            dd.setExecutable(true, false);
            File dir = new File(dd, "shared_prefs");
            dir.setReadable(true, false); dir.setExecutable(true, false);
            new File(dir, "carrier.xml").setReadable(true, false);
        } catch (Throwable ignored) { }

        // root fallback: stage a copy system_server / system apps can always read
        try {
            Process su = Runtime.getRuntime().exec("su");
            su.getOutputStream().write(("cat " + getApplicationInfo().dataDir
                    + "/shared_prefs/carrier.xml > /data/system/carrierspoof.conf\n"
                    + "chmod 644 /data/system/carrierspoof.conf\n").getBytes());
            su.getOutputStream().flush();
            su.waitFor();
        } catch (Throwable ignored) { }
    }

    private static String randomLine(String countryCode) {
        Random r = new Random();
        if ("SY".equalsIgnoreCase(countryCode)) {
            int op = 3 + r.nextInt(6); // 93x..98x prefixes
            return String.format(Locale.US, "+9639%d%07d", op, r.nextInt(10000000));
        }
        // US NANP: +1 NPA NXX XXXX (area/exchange can't start with 0/1, skip N11)
        int npa, nxx;
        do { npa = 200 + r.nextInt(800); } while (npa % 100 == 11);
        do { nxx = 200 + r.nextInt(800); } while (nxx % 100 == 11);
        return String.format(Locale.US, "+1%03d%03d%04d", npa, nxx, r.nextInt(10000));
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

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
