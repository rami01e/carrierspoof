package io.github.libxposed.api;

public class XposedModule implements XposedInterface {

    private final XposedInterface base;
    private final XposedModuleInterface.ModuleLoadedParam param;

    public XposedModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        this.base = base;
        this.param = param;
    }

    protected final XposedInterface getBase() { return base; }
    protected final XposedModuleInterface.ModuleLoadedParam getParam() { return param; }

    protected final void log(String message) { }

    public void onSystemServerLoaded(XposedModuleInterface.SystemServerLoadedParam param) { }

    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) { }
}
