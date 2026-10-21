package com.example.newapp;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

/** Configures App Check before any callable Firebase operation can be issued. */
public final class MaYaiOnApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // Register this debug token in Firebase Console > App Check for local testing only.
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
            Log.i("MaYaiOnAppCheck", "Debug App Check provider active; register the logged debug token in Firebase Console.");
        } else {
            appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance());
        }
    }
}
