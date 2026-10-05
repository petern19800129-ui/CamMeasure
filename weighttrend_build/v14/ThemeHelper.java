package za.co.petern.weighttrend;

import android.app.UiModeManager;
import android.content.Context;
import android.content.SharedPreferences;

public final class ThemeHelper {
    public static final String MODE_SYSTEM="system";
    public static final String MODE_LIGHT="light";
    public static final String MODE_DARK="dark";

    private static final String PREFS="appearance_settings";
    private static final String KEY_MODE="theme_mode";

    private ThemeHelper(){}

    public static String getMode(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getString(KEY_MODE,MODE_SYSTEM);
    }

    public static String label(String mode){
        if(MODE_LIGHT.equals(mode)) return "Light";
        if(MODE_DARK.equals(mode)) return "Dark";
        return "System";
    }

    public static void setMode(Context context,String mode){
        if(!MODE_LIGHT.equals(mode) && !MODE_DARK.equals(mode)) mode=MODE_SYSTEM;
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putString(KEY_MODE,mode).apply();
        applyMode(context,mode);
    }

    public static void applySavedMode(Context context){
        applyMode(context,getMode(context));
    }

    private static void applyMode(Context context,String mode){
        UiModeManager manager=context.getSystemService(UiModeManager.class);
        if(manager==null) return;

        if(MODE_DARK.equals(mode)){
            manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
        }else if(MODE_LIGHT.equals(mode)){
            manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO);
        }else{
            // For an application override, AUTO clears forced YES/NO and lets the
            // package inherit the phone's current night-mode configuration.
            manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO);
        }
    }
}
