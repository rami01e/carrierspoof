package io.github.libxposed.api;

public final class XposedBridge {

    private XposedBridge() { }

    public static void log(String message) { }
    public static void log(Throwable throwable) { }

    public static <T extends Hooker> XposedInterface.MethodUnhooker hook(
            Class<T> hooker,
            String className,
            String methodName,
            ClassLoader classLoader,
            Object... parameters) {
        return null;
    }
}
