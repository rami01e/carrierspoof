package com.kimera.carrierspoof;

import java.lang.reflect.Member;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LegacyHook implements IXposedHookLoadPackage {

    private static final String[] CLIENTS = {
            "com.google.android.gms", "com.android.vending", "com.google.android.gsf"};

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        Cfg.LOG = XposedBridge::log;
        if ("android".equals(p.packageName)) {
            Cfg.log("system_server — installing system framework hooks");
            SpoofCore.hookSystemServer(p.classLoader, LegacyHook::after);
        } else if ("com.android.phone".equals(p.packageName)) {
            Cfg.log("phone process — installing framework hooks");
            SpoofCore.hookPhone(p.classLoader, LegacyHook::after);
            SpoofCore.hookNetworkSource(p.classLoader, LegacyHook::before);
        } else {
            for (String c : CLIENTS)
                if (c.equals(p.packageName)) {
                    Cfg.log("client process " + c + " — installing client hooks");
                    SpoofCore.hookClient(p.classLoader, LegacyHook::after);
                }
        }
    }

    static void after(Member m, SpoofCore.CB cb) {
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        cb.run(param.thisObject, param.args, param.getResult(), param::setResult);
                    } catch (Throwable t) {
                        Cfg.log("callback: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            Cfg.log("hook failed: " + m + " → " + t);
        }
    }

    static void before(Member m, SpoofCore.CB cb) {
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        cb.run(param.thisObject, param.args, null, param::setResult);
                    } catch (Throwable t) {
                        Cfg.log("callback: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            Cfg.log("hook failed: " + m + " → " + t);
        }
    }
}
