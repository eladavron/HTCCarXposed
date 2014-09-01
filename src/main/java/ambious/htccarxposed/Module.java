package ambious.htccarxposed;

/**
 * Created by Elad on 01/06/2014.
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getSurroundingThis;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;

public class Module implements IXposedHookLoadPackage {
    private XSharedPreferences xSharedPreferences;

    Context _mainContext;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Loaded app: " + lpparam.packageName);

        xSharedPreferences = new XSharedPreferences("ambious.htccarxposed", "xPreferences"); //Load the user preferences file called 'xPreferences' - since it's world-readable while the default preference file isn't.
        if (!(lpparam.packageName.equals("com.htc.AutoMotive") || lpparam.packageName.equals("android"))) //Make sure we only operate within the car-app
            return;

        /**
         * Multitasking - this part intercepts "Recent Apps" requests, and if "Autmotive" (carmode) is set to "true", it over-rides it to "false", thus allowing multi-tasking.
         */
        if (lpparam.packageName.equals("android")) {
            XposedBridge.log("htccarxposed: Hooked into Android Policy!");
            final boolean allow_multitasking = xSharedPreferences.getBoolean("allow_multitasking", false); //Check user settings - default is false
            Class<?> HandleRecentAppsRunnable = findClass("com.android.internal.policy.impl.PhoneWindowManager.HandleRecentAppsRunnable",lpparam.classLoader);
            findAndHookMethod(HandleRecentAppsRunnable, "run", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (getBooleanField(getSurroundingThis(param.thisObject),"mAutoMotiveEnabled") && allow_multitasking) //Only intercepts if "allow multitasking" is set and car mode is active.
                    {
                        XposedBridge.log("htccarxposed: Intercepted Recent Apps request!");
                        setBooleanField(getSurroundingThis(param.thisObject), "mAutoMotiveEnabled", false);
                    }
                }
            });
            return;
        }

        /**
         * Anything beyond this point is on com.htc.Automotive
         */

        XposedBridge.log("htccarxposed: HTC Automotive Loaded!");


        /**
         * Innitiate Context. This create a 'mainContext' object that holds the car app's instance.
         */

        findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Context _this = (Context) param.thisObject; //Gets the active app instance
                _mainContext = _this;
            }
        });

        /**
         * Enable statusbar - also displays the 'recent-apps' while not actually enabling it.
         */
        final boolean allow_pulldown = xSharedPreferences.getBoolean("allow_pulldown", true); //Check user settings - default is true
        if (allow_pulldown) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "setStatusBarLocked", boolean.class, new XC_MethodReplacement() {

                /**
                 * Intercepts the method and completely replaces it.
                 */
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("htccarxposed: Statusbar released.");
                    return null;
                }
            });
        }

        /**
         * Home button - currently works by intercepting and bypassing a method called 'enableUiMode', but it may break other things.
         */
        //TODO: This might be a risky way to perform this, check if you can actively remove the 'HOME' intent instead

        boolean capture_home = xSharedPreferences.getBoolean("capture_home", true);
        if (!capture_home) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "enableUiMode", new XC_MethodReplacement() {
                /**
                 * Intercepts the method and completely replaces it.
                 */
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("htccarxposed: Home button released.");
                    return null;
                }
            });
        }

        /**
         * Wakelock mod - enabled by default.
         * Sets whether to keep screen on or not.
         */
        boolean wakelock = xSharedPreferences.getBoolean("wakelock",true);
        if (!wakelock)
        {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "acquireWakeLock", boolean.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("htccarxposed: Wakelock released.");
                    return null;
                }
            });
        }

        /**
         * Startup Wi-Fi Action
         */
        final int wifi_mode = xSharedPreferences.getInt("wifi_mode", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        if (wifi_mode != 0) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {

                /**
                 * Works by intercepting the startup method and adding code after it runs
                 */
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                    if (wifi_mode == 1) {
                        wifiManager.setWifiEnabled(true);
                        XposedBridge.log("htccarxposed: Turning WIFI on!");
                    } else if (wifi_mode == 2) {
                        wifiManager.setWifiEnabled(false);
                        XposedBridge.log("htccarxposed: Turning WIFI off!");
                    }
                }
            });
        }

        /*
        * Exit Wi-Fi Action
        */

        final int wifi_exit = xSharedPreferences.getInt("wifi_exit", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        if (wifi_exit != 0) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onPause", new XC_MethodHook() {
                @Override
                /**
                 * Works by intercepting the exit method and adding code BEFORE it runs (adding it after just won't work).
                 */
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                    if (wifi_exit == 1) {
                        wifiManager.setWifiEnabled(true);
                        XposedBridge.log("htccarxposed: Turning WIFI on!");
                    } else if (wifi_exit == 2) {
                        wifiManager.setWifiEnabled(false);
                        XposedBridge.log("htccarxposed: Turning WIFI off!");
                    }
                }
            });
        }

        /**
         * Log and kill all apps opened during session
         */
        if (xSharedPreferences.getBoolean("kill_apps",false)) {
            findAndHookMethod("com.htc.AutoMotive.util.Utils", lpparam.classLoader, "safeStartActivity", Context.class, Intent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Intent _intent = (Intent) param.args[1]; //Add the new intent to list
                    XposedBridge.log("htccarxposed: adding " + _intent.getComponent().getPackageName() + " to intent array.");
                    callService(Killer.ADD_TO_LIST,"packageName",_intent.getComponent().getPackageName());
                }
            });

            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    XposedBridge.log("htccarxposed: sending kill command.");
                    callService(Killer.KILL_SWITCH);
                }
            });
        }

        /**
         * Circumvent 'exit' dialog'
         */
        final boolean bypassExitDialog = xSharedPreferences.getBoolean("bypassExitDialog", true);
        if (bypassExitDialog) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (getBooleanField(param.thisObject, "bIsLeaveDialogShowing")) { //Intercepts exit dialog
                        XposedBridge.log("htccarxposed: Bypassing exit dialog");
                        callMethod(param.thisObject, "finishCarmode");
                    }
                }
            });
        }
    }

    private void callService(int action, String... args)
    {
        Intent newIntent = new Intent();
        newIntent.putExtra("action",action);
        newIntent.setComponent(new ComponentName("ambious.htccarxposed", "ambious.htccarxposed.Killer"));
        if (action == Killer.ADD_TO_LIST)
            newIntent.putExtra(args[0],args[1]);
        _mainContext.startService(newIntent);
    }
}