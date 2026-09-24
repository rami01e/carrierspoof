package io.github.libxposed.api;

import java.lang.reflect.Member;

public interface XposedInterface {

    interface BeforeHookCallback {
        Object[] getArgs();
        Object getThisObject();
        Member getMember();
        void returnAndSkip(Object result);
    }

    interface AfterHookCallback {
        Object[] getArgs();
        Object getThisObject();
        Member getMember();
        Object getResult();
        void setResult(Object result);
        Throwable getThrowable();
        void setThrowable(Throwable throwable);
    }

    interface MethodUnhooker {
        void unhook();
    }
}
