package com.example.stellog;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * 应用入口，启动时应用已保存的深色模式设置。
 */
public class StellogApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences("main_preferences", MODE_PRIVATE)
                .getInt("night_mode", AppCompatDelegate.MODE_NIGHT_NO);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
