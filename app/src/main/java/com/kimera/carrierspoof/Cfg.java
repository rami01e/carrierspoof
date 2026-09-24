package com.kimera.carrierspoof;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.FileReader;

final class Cfg {
    static final String PKG = "com.kimera.carrierspoof";

    interface LogFn { void log(String s); }
    static volatile LogFn LOG = s -> { };

    // defaults = T-Mobile US
    static volatile String NUMERIC = "310260", ALPHA = "T-Mobile", COUNTRY = "us",
            IMSI = "310260123456789", ICCID = "890126000000000001", LINE = "+15551234567";

    private static volatile long lastCheck = 0L;

    static int mcc() { try { return Integer.parseInt(NUMERIC.substring(0, 3)); } catch (Throwable t) { return 310; } }
    static int mnc() { try { return Integer.parseInt(NUMERIC.substring(3)); } catch (Throwable t) { return 260; } }

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
                    x.getString("country", null), x.getString("imsi", null),
                    x.getString("iccid", null), x.getString("line", null))) return;
        } catch (Throwable ignored) { }

        for (String path : new String[]{
                "/data/data/" + PKG + "/shared_prefs/carrier.xml",
                "/data/system/carrierspoof.conf"}) {
            try {
                XmlPullParser p = Xml.newPullParser();
                p.setInput(new FileReader(path));
                String n = null, a = null, c = null, im = null, ic = null, li = null;
                int ev = p.getEventType();
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "string".equals(p.getName())) {
                        String k = p.getAttributeValue(null, "name");
                        String v = p.nextText();
                        if (k != null && v != null) switch (k) {
                            case "numeric": n = v; break;
                            case "alpha":   a = v; break;
                            case "country": c = v; break;
                            case "imsi":    im = v; break;
                            case "iccid":   ic = v; break;
                            case "line":    li = v; break;
                        }
                    }
                    ev = p.next();
                }
                if (apply(n, a, c, im, ic, li)) { log("config from " + path); return; }
            } catch (Throwable ignored) { }
        }
    }

    private static boolean apply(String n, String a, String c, String im, String ic, String li) {
        if (n == null || n.length() < 5 || n.length() > 6) return false;
        NUMERIC = n;
        if (a != null) ALPHA = a;
        if (c != null) COUNTRY = c;
        IMSI = (im != null) ? im : (n + "123456789");
        ICCID = (ic != null) ? ic : "890126000000000001";
        if (li != null) LINE = li;
        return true;
    }
}
