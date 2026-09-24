package de.robv.android.xposed;

import java.util.Collections;
import java.util.Map;

public class XSharedPreferences {
    public XSharedPreferences(String packageName) { }
    public XSharedPreferences(String packageName, String prefFileName) { }
    public void reload() { }
    public void makeWorldReadable() { }
    public Map<String, ?> getAll() { return Collections.emptyMap(); }
    public String getString(String key, String defValue) { return defValue; }
    public boolean getBoolean(String key, boolean defValue) { return defValue; }
    public int getInt(String key, int defValue) { return defValue; }
}
