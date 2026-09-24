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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class PickerActivity extends Activity {

    private static final class Carrier {
        final String name;      // picker label
        final String alpha;     // operator name shown on the device (SPN)
        final String country;   // ISO country
        final String[] plmn;    // "MCC-MNC" pairs
        Carrier(String name, String alpha, String country, String[] plmn) {
            this.name = name; this.alpha = alpha; this.country = country; this.plmn = plmn;
        }
    }

    // Currently-operating US carriers. MCC/MNC from the ITU / Wikipedia
    // "Mobile Network Codes in ITU region 3xx (North America)" table.
    private static final Carrier[] CARRIERS = {
        new Carrier("AT&T",              "AT&T",              "us", new String[]{
                "310-016","310-030","310-070","310-090","310-150","310-170",
                "310-280","310-380","310-410","310-560","310-680","311-180"}),
        new Carrier("T-Mobile",          "T-Mobile",          "us", new String[]{
                "310-026","310-160","310-200","310-210","310-220","310-230",
                "310-240","310-250","310-260","310-270","310-290","310-300",
                "310-310","310-330","310-660","310-800"}),
        new Carrier("Verizon",           "Verizon",           "us", new String[]{
                "310-004","310-012","310-013","310-590","310-890",
                "311-270","311-271","311-272","311-273","311-274","311-275",
                "311-276","311-277","311-278","311-279","311-280",
                "311-480","311-481","311-482","311-483","311-484","311-485",
                "311-486","311-487","311-488","311-489"}),
        new Carrier("UScellular",        "UScellular",        "us", new String[]{
                "311-220","311-221","311-222","311-223","311-224","311-225",
                "311-226","311-227","311-228","311-229","311-230"}),
        new Carrier("Google Fi",         "Google Fi",         "us", new String[]{
                "310-260","310-240","310-210"}),
        new Carrier("Metro by T-Mobile", "Metro by T-Mobile", "us", new String[]{
                "311-660","310-260","310-240"}),
        new Carrier("Cricket Wireless",  "Cricket",           "us", new String[]{
                "310-150","310-410","310-016"}),
        new Carrier("Mint Mobile",       "Mint Mobile",       "us", new String[]{
                "310-260","310-240","310-210"}),
    };

    private Spinner carrierSpin, plmnSpin;
    private TextView current, status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20); root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("CarrierSpoof by KiMeRa");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(10));
        root.addView(title);

        SharedPreferences sp = getSharedPreferences("carrier", MODE_PRIVATE);

        current = new TextView(this);
        current.setTextSize(13);
        current.setPadding(0, 0, 0, dp(10));
        root.addView(current);
        refreshCurrent(sp);

        List<String> names = new ArrayList<>();
        for (Carrier c : CARRIERS) names.add(c.name);

        carrierSpin = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        carrierSpin.setAdapter(ad);

        plmnSpin = new Spinner(this);
        plmnSpin.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, new ArrayList<String>()));

        String savedName = sp.getString("name", CARRIERS[1].name); // default T-Mobile
        int ci = 0;
        for (int i = 0; i < CARRIERS.length; i++)
            if (CARRIERS[i].name.equals(savedName)) { ci = i; break; }
        carrierSpin.setSelection(ci);
        updatePlmnSpinner(ci, sp.getString("numeric", "310-260"));

        carrierSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View v, int pos, long id) {
                updatePlmnSpinner(pos, null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        root.addView(carrierSpin);
        root.addView(plmnSpin);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        Button save = new Button(this);  save.setText("Save selection");
        Button rand = new Button(this);  rand.setText("Random");
        Button reset = new Button(this); reset.setText("Reset to T-Mobile (default)");

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, 0);

        save.setOnClickListener(v -> apply(carrierSpin.getSelectedItemPosition(),
                plmnSpin.getSelectedItemPosition()));
        rand.setOnClickListener(v -> randomPick());
        reset.setOnClickListener(v -> {
            int tci = 1; // T-Mobile
            int tpi = indexOf(tci, "310-260");
            carrierSpin.setSelection(tci);
            updatePlmnSpinner(tci, "310-260");
            apply(tci, tpi);
        });

        root.addView(save, lp);
        root.addView(rand, lp);
        root.addView(reset, lp);
        root.addView(status);
        setContentView(root);
    }

    private void updatePlmnSpinner(int carrierIdx, String select) {
        List<String> opts = new ArrayList<>();
        for (String s : CARRIERS[carrierIdx].plmn) opts.add(s);
        plmnSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opts));
        if (select != null) {
            int i = indexOf(carrierIdx, select);
            if (i >= 0) plmnSpin.setSelection(i);
        }
    }

    private int indexOf(int carrierIdx, String numeric) {
        for (int i = 0; i < CARRIERS[carrierIdx].plmn.length; i++)
            if (CARRIERS[carrierIdx].plmn[i].equals(numeric)) return i;
        return -1;
    }

    private void randomPick() {
        Random r = new Random();
        int ci = r.nextInt(CARRIERS.length);
        int pi = r.nextInt(CARRIERS[ci].plmn.length);
        carrierSpin.setSelection(ci);
        updatePlmnSpinner(ci, null);
        plmnSpin.setSelection(pi);
        apply(ci, pi);
    }

    private void apply(int ci, int pi) {
        if (pi < 0) pi = 0;
        Carrier c = CARRIERS[ci];
        String numeric = c.plmn[pi].replace("-", ""); // "310260"

        Random r = new Random();
        String imsi  = numeric + String.format(Locale.US, "%09d", r.nextInt(900000000) + 100000000);
        String iccid = "891" + numeric + String.format(Locale.US, "%010d", r.nextInt(1000000000));

        getSharedPreferences("carrier", MODE_PRIVATE).edit()
                .putString("name", c.name)
                .putString("numeric", numeric)
                .putString("alpha", c.alpha)
                .putString("country", c.country)
                .putString("imsi", imsi)
                .putString("iccid", iccid)
                .putString("line", "+15551234567")
                .commit();

        chmodPrefs();
        refreshCurrent(getSharedPreferences("carrier", MODE_PRIVATE));
        status.setText("Saved: " + c.name + " (" + c.plmn[pi] + ")\n"
                + "Now reboot your device to see the carrier info changed!");
    }

    private void refreshCurrent(SharedPreferences sp) {
        current.setText("Current: " + sp.getString("alpha", "T-Mobile")
                + " (" + sp.getString("numeric", "310260") + ")");
    }

    private void chmodPrefs() {
        try {
            File dd = new File(getApplicationInfo().dataDir);
            dd.setExecutable(true, false);
            File dir = new File(dd, "shared_prefs");
            dir.setReadable(true, false); dir.setExecutable(true, false);
            new File(dir, "carrier.xml").setReadable(true, false);
        } catch (Throwable ignored) { }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
