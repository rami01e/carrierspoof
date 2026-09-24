package io.github.libxposed.api;

public interface XposedModuleInterface {

    interface ModuleLoadedParam {
        String getProcessName();
        String getPackageName();
        int getUid();
        boolean isSystemServer();
    }

    interface SystemServerLoadedParam {
        ClassLoader getClassLoader();
    }

    interface PackageLoadedParam {
        String getPackageName();
        ClassLoader getClassLoader();
        boolean isFirstPackage();
    }
}
