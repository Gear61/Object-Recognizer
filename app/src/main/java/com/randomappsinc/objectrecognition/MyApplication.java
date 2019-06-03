package com.randomappsinc.objectrecognition;

import android.app.Application;

import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.IoniconsModule;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Iconify.with(new IoniconsModule());
    }
}
