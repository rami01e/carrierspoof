package dev.local.carrierspoof;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;
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

    // name, numeric (MCC+MNC), SPN shown to apps, ISO country, ICCID prefix
    private static final String[][] CARRIERS = {
        {"T-Mobile US",       "310260", "T-Mobile",          "us", "8901260"},
        {"AT&T",              "310410", "AT&T",              "us", "8901410"},
        {"Verizon",           "310004", "Verizon",           "us", "891480"},
        {"Sprint",            "310120", "Sprint",            "us", "8901120"},
        {"UScellular",        "311580", "UScellular",        "us", "8901160"},
        {"Google Fi",         "310260", "Google Fi",         "us", "8901260"},
        {"Metro by T-Mobile", "311660", "Metro by T-Mobile", "us", "8901260"},
        {"Cricket Wireless",  "310150", "Cricket",           "us", "8901410"},
        {"Mint Mobile",       "311160", "Mint Mobile",       "us", "8901260"},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20); root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("Carrier Spoof — MuMu");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(10));

        SharedPreferences sp = getSharedPreferences("carrier", MODE_PRIVATE);
        TextView cur = new TextView(this);
        cur.setTextSize(13);
        cur.setText("Current: " + sp.getString("alpha", "T-Mobile")
                + " (" + sp.getString("numeric", "310260") + ")");
        cur.setPadding(0, 0, 0, dp(10));

        List<String> names = new ArrayList<>();
        for (String[] c : CARRIERS) names.add(c[0]);
        Spinner spin = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(ad);
        String curName = sp.getString("name", "T-Mobile US");
        for (int i = 0; i < CARRIERS.length; i++) if (CARRIERS[i][0].equals(curName)) spin.setSelection(i);

        Button save = new Button(this);  save.setText("Save selection");
        Button reset = new Button(this); reset.setText("Reset to T-Mobile US (default)");
        TextView status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(12), 0, 0);

        save.setOnClickListener(v -> apply(spin.getSelectedItemPosition(), status));
        reset.setOnClickListener(v -> { spin.setSelection(0); apply(0, status); });

        root.addView(title);
        root.addView(cur);
        root.addView(spin);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(save, lp);
        root.addView(reset, lp);
        root.addView(status);
        setContentView(root);
    }

    private void apply(int i, TextView status) {
        String[] c = CARRIERS[i];
        Random r = new Random();
        String imsi  = c[1] + String.format(Locale.US, "%09d", r.nextInt(900000000) + 100000000);
        String iccid = c[4] + String.format(Locale.US, "%012d", (long) (r.nextDouble() * 999999999999.0));
        getSharedPreferences("carrier", MODE_PRIVATE).edit()
                .putString("name", c[0]).putString("numeric", c[1]).putString("alpha", c[2])
                .putString("country", c[3]).putString("imsi", imsi).putString("iccid", iccid)
                .putString("line", "+15551234567").commit();

        // make prefs readable by hooked processes (Vector's prefs bridge / direct read)
        try {
            File dd = new File(getApplicationInfo().dataDir);
            dd.setExecutable(true, false);
            File dir = new File(dd, "shared_prefs");
            dir.setReadable(true, false); dir.setExecutable(true, false);
            new File(dir, "carrier.xml").setReadable(true, false);
        } catch (Throwable ignored) { }

        // OPTIONAL hard fallback (grant CarrierSpoof root once in KernelSU): stage a copy
        // the phone process can always read, SELinux or not. Uncomment to enable:
        // try {
        //     Process su = Runtime.getRuntime().exec("su");
        //     su.getOutputStream().write(("cat " + getApplicationInfo().dataDir
        //         + "/shared_prefs/carrier.xml > /data/system/carrierspoof.conf\n"
        //         + "chmod 644 /data/system/carrierspoof.conf\n").getBytes());
        //     su.getOutputStream().flush();
        //     su.waitFor();
        // } catch (Throwable ignored) { }

        status.setText("Saved: " + c[0] + " (" + c[1] + ")\nNow: reboot VM, then wipe data of\n"
                + "Google Services Framework + Play services + Play Store.");
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
