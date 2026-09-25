package com.kimera.carrierspoof;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.FileReader;

final class Cfg {
    static final String PKG = "com.kimera.carrierspoof";

    interface LogFn { void log(String s); }
    static volatile LogFn LOG = s -> { };

    // defaults = Verizon US, SIM1 on
    static volatile String NUMERIC = "310004", ALPHA = "Verizon Wireless", SPN = "Verizon",
            COUNTRY = "us", IMSI = "310004123456789", ICCID = "891480000000000001",
            LINE = "+12025550134", SIM1 = "on";

    private static volatile long lastCheck = 0L;

    static int mcc() { try { return Integer.parseInt(NUMERIC.substring(0, 3)); } catch (Throwable t) { return 310; } }
    static int mnc() { try { return Integer.parseInt(NUMERIC.substring(3)); } catch (Throwable t) { return 4; } }
    static boolean sim1On() { return !"off".equals(SIM1); }

    static void log(String s) { try { LOG.log("[CarrierSpoof] " + s); } catch (Throwable ignored) { } }

    static synchronized void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 2000L) return;
        lastCheck = now;

        try {
            de.robv.android.xposed.XSharedPreferences x =
                    new de.robv.android.xposed.XSharedPreferences(PKG, "carrier");
            x.reload();
            if (apply(x.getString("numeric", null), x.getString("alpha", null),
                    x.getString("spn", null), x.getString("country", null),
                    x.getString("imsi", null), x.getString("iccid", null),
                    x.getString("line", null), x.getString("sim1", null))) return;
        } catch (Throwable ignored) { }

        for (String path : new String[]{
                "/data/data/" + PKG + "/shared_prefs/carrier.xml",
                "/data/system/carrierspoof.conf"}) {
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(new FileReader(path));
                String n = null, a = null, sp = null, c = null, im = null, ic = null,
                       li = null, s1 = null;
                int ev = p.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "string".equals(p.getName())) {
                        String k = p.getAttributeValue(null, "name");
                        String v = p.nextText();
                        if (k != null && v != null) switch (k) {
                            case "numeric": n = v; break;
                            case "alpha":   a = v; break;
                            case "spn":     sp = v; break;
                            case "country": c = v; break;
                            case "imsi":    im = v; break;
                            case "iccid":   ic = v; break;
                            case "line":    li = v; break;
                            case "sim1":    s1 = v; break;
                        }
                    }
                    ev = p.next();
                }
                if (apply(n, a, sp, c, im, ic, li, s1)) { log("config from " + path); return; }
            } catch (Throwable ignored) { }
        }
    }

    private static boolean apply(String n, String a, String sp, String c,
                                 String im, String ic, String li, String s1) {
        if (n == null || n.length() < 5 || n.length() > 6) return false;
        NUMERIC = n;
        if (a != null) ALPHA = a;
        if (sp != null) SPN = sp;
        if (c != null) COUNTRY = c;
        IMSI = (im != null) ? im : (n + "123456789");
        ICCID = (ic != null) ? ic : "891480000000000001";
        if (li != null) LINE = li;
        if (s1 != null) SIM1 = s1;
        return true;
    }
}
