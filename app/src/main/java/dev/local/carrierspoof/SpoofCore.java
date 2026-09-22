package dev.local.carrierspoof;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;

final class SpoofCore {

    interface Ret { void set(Object v) throws Throwable; }
    interface CB { void run(Object thiz, Object[] args, Object result, Ret ret) throws Throwable; }
    interface After { void apply(Member m, CB cb); }

    private static final CB NUM = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.NUMERIC); };
    private static final CB ALP = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.ALPHA); };
    private static final CB CTR = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.COUNTRY); };
    private static final CB ICC = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.ICCID); };
    private static final CB IMS = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.IMSI); };
    private static final CB LIN = (t, a, r, ret) -> { Cfg.refresh(); ret.set(Cfg.LINE); };

    /** name-based classifier — survives ROM/version method-name drift */
    private static CB mapFor(String n) {
        if (n.contains("OperatorName") || n.contains("ServiceProviderName")
                || n.contains("OperatorAlpha")) return ALP;
        if (n.contains("OperatorNumeric") || n.contains("SimOperator")
                || n.contains("NetworkOperator")) return NUM;
        if (n.contains("CountryIso")) return CTR;
        if (n.contains("IccSerialNumber") || n.contains("IccId")) return ICC;
        if (n.contains("SubscriberId") || n.equals("getIMSI") || n.contains("Imsi")) return IMS;
        if (n.contains("Line1Number") || n.contains("Msisdn") || n.contains("LineNumber")) return LIN;
        return null;
    }

    static void hookPhone(ClassLoader cl, After after) {
        // 1) SubscriptionController — rewrite every SubscriptionInfo handed to any app
        try {
            Class<?> ctrl = cl.loadClass("com.android.internal.telephony.SubscriptionController");
            Class<?> info = cl.loadClass("com.android.internal.telephony.SubscriptionInfo");
            CB rw = (t, a, r, ret) -> rewriteSubInfoTree(r, info);
            int n = 0;
            for (Method m : ctrl.getDeclaredMethods()) {
                Class<?> rt = m.getReturnType();
                // hook every non-primitive getter; rewriter no-ops on non-matching results
                // (covers both List and ParceledListSlice return types across ROMs)
                if (m.getName().startsWith("get") && rt != void.class && !rt.isPrimitive()) {
                    after.apply(m, rw); n++;
                }
            }
            Cfg.log("SubscriptionController: " + n + " methods hooked");
        } catch (Throwable t) { Cfg.log("SubscriptionController: " + t); }

        // 2) binder endpoints (ITelephony / IPhoneSubInfo) + UICC records — what GMS actually pulls
        int hooked = 0;
        for (String cn : new String[]{
                "com.android.phone.PhoneInterfaceManager",
                "com.android.internal.telephony.PhoneSubInfoController",
                "com.android.internal.telephony.uicc.IccRecords",
                "com.android.internal.telephony.uicc.SIMRecords",
                "com.android.internal.telephony.uicc.RuimRecords"}) {
            try {
                for (Method m : cl.loadClass(cn).getDeclaredMethods()) {
                    Class<?> rt = m.getReturnType();
                    if (rt.getSimpleName().equals("ServiceState")) {
                        after.apply(m, (t, a, r, ret) -> rewriteServiceState(r)); hooked++;
                    } else if (rt == String.class) {
                        CB cb = mapFor(m.getName());
                        if (cb != null) { after.apply(m, cb); hooked++; }
                    }
                }
            } catch (Throwable t) { Cfg.log(cn + ": " + t); }
        }
        Cfg.log("binder/uicc endpoints hooked: " + hooked);
    }

    static void hookClient(ClassLoader cl, After after) {
        try {
            for (Method m : cl.loadClass("android.telephony.TelephonyManager").getDeclaredMethods()) {
                if (m.getReturnType() != String.class) continue;
                CB cb = mapFor(m.getName());
                if (cb != null) after.apply(m, cb);
            }
        } catch (Throwable t) { Cfg.log("TelephonyManager: " + t); }

        try {
            Class<?> info = cl.loadClass("android.telephony.SubscriptionInfo");
            CB rw = (t, a, r, ret) -> rewriteSubInfoTree(r, info);
            for (Method m : cl.loadClass("android.telephony.SubscriptionManager").getDeclaredMethods()) {
                Class<?> rt = m.getReturnType();
                if (rt == info || Collection.class.isAssignableFrom(rt)) after.apply(m, rw);
            }
            for (Method m : info.getDeclaredMethods()) {
                String n = m.getName(); Class<?> rt = m.getReturnType();
                if (rt == String.class || rt == CharSequence.class) {
                    if (n.equals("getCarrierName") || n.equals("getDisplayName")) after.apply(m, ALP);
                    else if (n.equals("getIccId")) after.apply(m, ICC);
                    else if (n.equals("getCountryIso") || n.equals("getCountryIsoString")) after.apply(m, CTR);
                    else if (n.equals("getMccString")) after.apply(m, (t, a, r, ret) -> ret.set(Cfg.NUMERIC.substring(0, 3)));
                    else if (n.equals("getMncString")) after.apply(m, (t, a, r, ret) -> ret.set(Cfg.NUMERIC.substring(3)));
                    else if (n.equals("getNumber")) after.apply(m, LIN);
                } else if (rt == int.class) {
                    if (n.equals("getMcc")) after.apply(m, (t, a, r, ret) -> ret.set(Cfg.mcc()));
                    else if (n.equals("getMnc")) after.apply(m, (t, a, r, ret) -> ret.set(Cfg.mnc()));
                }
            }
        } catch (Throwable t) { Cfg.log("SubscriptionManager: " + t); }
    }

    // ---------- object rewriters (pure reflection, no Xposed API) ----------
    private static void rewriteSubInfoTree(Object r, Class<?> info) {
        if (r == null) return;
        if (info.isInstance(r)) { rewriteSubInfo(r); return; }
        if (r instanceof Collection) {
            for (Object o : (Collection<?>) r) if (info.isInstance(o)) rewriteSubInfo(o);
            return;
        }
        try { // ParceledListSlice on some ROM variants
            Object list = r.getClass().getMethod("getList").invoke(r);
            if (list instanceof Collection)
                for (Object o : (Collection<?>) list) if (info.isInstance(o)) rewriteSubInfo(o);
        } catch (Throwable ignored) { }
    }

    private static void rewriteSubInfo(Object o) {
        Cfg.refresh();
        setF(o, "mMcc", Cfg.mcc());
        setF(o, "mMnc", Cfg.mnc());
        setF(o, "mMccString", Cfg.NUMERIC.substring(0, 3));
        setF(o, "mMncString", Cfg.NUMERIC.substring(3));
        setF(o, "mCarrierName", Cfg.ALPHA);
        setF(o, "mDisplayName", Cfg.ALPHA);
        setF(o, "mIccId", Cfg.ICCID);
        setF(o, "mCountryIso", Cfg.COUNTRY);
        setF(o, "mNumber", Cfg.LINE);
        setF(o, "mCarrierId", -1); // kill carrier-id lookup resolving to China Mobile
    }

    private static void rewriteServiceState(Object ss) { // network identity from the vRIL, not just SIM
        if (ss == null) return;
        Cfg.refresh();
        for (Class<?> c = ss.getClass(); c != null && c != Object.class; c = c.getSuperclass())
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                String n = f.getName();
                try {
                    f.setAccessible(true);
                    if (n.contains("OperatorNumeric")) f.set(ss, Cfg.NUMERIC);
                    else if (n.contains("Alpha")) f.set(ss, Cfg.ALPHA);
                    else if (n.contains("CountryIso")) f.set(ss, Cfg.COUNTRY);
                } catch (Throwable ignored) { }
            }
    }

    private static void setF(Object o, String name, Object v) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(o, v);
        } catch (Throwable ignored) { }
    }
}
