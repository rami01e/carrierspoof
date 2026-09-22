package dev.local.carrierspoof;

import java.lang.reflect.Member;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleContext;

public class MainHook extends XposedModule {

    private static final String[] CLIENTS = {
            "com.google.android.gms", "com.android.vending", "com.google.android.gsf"};

    public MainHook(XposedModuleContext context) {
        super(context);
        Cfg.LOG = this::log;
        Cfg.log("module loaded (modern API), pid " + android.os.Process.myPid());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        String pkg = param.getPackageName();
        if ("com.android.phone".equals(pkg)) {
            Cfg.log("phone process — installing framework hooks");
            SpoofCore.hookPhone(param.getClassLoader(), this::after);
        } else {
            for (String c : CLIENTS)
                if (c.equals(pkg)) {
                    Cfg.log("client process " + pkg + " — installing client hooks");
                    SpoofCore.hookClient(param.getClassLoader(), this::after);
                }
        }
    }

    /* ================= THE ONE API TOUCHPOINT =================
       If anything here fails to compile, open External Libraries →
       io.github.libxposed:api:101 → io.github.libxposed.api.XposedInterface
       and match the names. Likely deltas:
         - hookAfter(...) may be hookMethod(...)
         - afterHook(ThisParam) may be named differently
         - uncomment the beforeHook override if the compiler demands it
       Nothing else in this project touches the Xposed API. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void after(Member m, SpoofCore.CB cb) {
        try {
            hookAfter(m, new XposedInterface.MethodHooker() {
                @Override
                public void afterHook(XposedInterface.ThisParam p) {
                    try {
                        cb.run(p.getThis(), p.getArgs(), p.getResult(),
                                (Object v) -> p.setResult(v));
                    } catch (Throwable t) {
                        Cfg.log("callback: " + t);
                    }
                }
                // @Override public void beforeHook(XposedInterface.ThisParam p) {}
            });
        } catch (Throwable t) {
            Cfg.log("hookAfter failed: " + m + " → " + t);
        }
    }
}
