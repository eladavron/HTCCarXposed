package ambious.htccarxposed;

/**
 * Created by Elad on 01/06/2014.
 */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;

public class Module implements IXposedHookLoadPackage {
    private XSharedPreferences xSharedPreferences;
    private ArrayList<Intent> intentArray;
    Context _mainContext;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log("Loaded app: " + lpparam.packageName);

        xSharedPreferences = new XSharedPreferences("ambious.htccarxposed", "xPreferences"); //Load the user preferences file called 'xPreferences' - since it's world-readable while the default preference file isn't.
        if (!lpparam.packageName.equals("com.htc.AutoMotive")) //Make sure we only operate within the car-app
            return;

        XposedBridge.log("htccarxposed: HTC Automotive Loaded!");

        /**
         * Innitiate Context
         */

        findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            /**
             * Works by intercepting the startup method and adding code after it runs
             */
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Context _this = (Context) param.thisObject; //Gets the active app instance
                _mainContext = _this;
            }
        });

        /**
         * Multitasking - currently breaks dialer functionality
         */
        final boolean allow_multitasking = xSharedPreferences.getBoolean("allow_multitasking", false); //Check user settings - default is false
        if (allow_multitasking) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "sendBroadCast", boolean.class, new XC_MethodHook() {

                /**
                 * Intercepts the method and force it to broadcast 'false', which enables the 'recent-apps' button but also breaks the in-app dialer for some reason.
                 */
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    XposedBridge.log("htccarxposed: Attempting to enable multi-tasking!");
                    param.args[0] = false;
                }
            });
        }

        final boolean allow_pulldown = xSharedPreferences.getBoolean("allow_pulldown", true); //Check user settings - default is true
        /**
         * Enable statusbar - also displays the 'recent-apps' while not actually enabling it.
         */
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
         * Startup mods
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

        final int gps_mode = xSharedPreferences.getInt("gps_mode", 1); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        if (gps_mode != 0) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                /**
                 * Works by intercepting the startup method and adding code after it runs
                 */
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    Context _this = (Context) param.thisObject; //Gets the active app instance
                    if (gps_mode == 1) {
                        XposedBridge.log("htccarxposed: Turning GPS on!");
                        turnGpsOn(_this);
                    } else if (gps_mode == 2) {
                        XposedBridge.log("htccarxposed: Turning GPS off!");
                        turnGpsOff(_this);
                    }
                }
            });
        }

        /*
        * Exit mods
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


        final int gps_exit = xSharedPreferences.getInt("gps_exit", 1);
        if (gps_exit != 0) {
            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onPause", new XC_MethodHook() {

                /**
                 * Works by intercepting the exit method and adding code BEFORE it runs (adding it after just won't work).
                 */
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Context _this = (Context) param.thisObject; //Gets the active app instance
                    if (gps_exit == 1) {
                        XposedBridge.log("htccarxposed: Turning GPS on!");
                        turnGpsOn(_this);
                    } else if (gps_exit == 2) {
                        XposedBridge.log("htccarxposed: Turning GPS off!");
                        turnGpsOff(_this);
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
                    Context _this = (Context) param.thisObject;
                    Context _context = (Context) param.args[0]; //Unused
                    Intent _intent = (Intent) param.args[1]; //Add the new intent to list
                    XposedBridge.log("htccarxposed: adding " + _intent.getComponent().getPackageName() + " to intent array.");
                    Intent newIntent = new Intent(Intent.ACTION_RUN);
                    newIntent.setComponent(new ComponentName("ambious.htccarxposed", "ambious.htccarxposed.Killer"));
                    newIntent.putExtra("packageName", _intent.getComponent().getPackageName());
                    _mainContext.startService(newIntent);
                }
            });

            findAndHookMethod("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Context _this = (Context) param.thisObject; //Gets the active app instance
                    XposedBridge.log("htccarxposed: sending kill command.");
                    Intent newIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    newIntent.setComponent(new ComponentName("ambious.htccarxposed", "ambious.htccarxposed.Killer"));
                    _mainContext.startService(newIntent);
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



    /**
     * GPS it tricky business - for privacy reasons, apps should not be able to turn it on and off.
     * Instead, we use a 'root' trick that's otherwise not accessible to apps.
     * Since Xposed requires root, we're assuming the device is system-writable.
     * Honestly though I have no idea how this works - it's copied code from http://stackoverflow.com/a/18946258/601934
     */
    private String beforeEnable;
    private void turnGpsOn (Context context) {
        beforeEnable = Settings.Secure.getString (context.getContentResolver(),
                Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        String newSet = String.format ("%s,%s",
                beforeEnable,
                LocationManager.GPS_PROVIDER);
        try {
            Settings.Secure.putString (context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                    newSet);
        } catch(Exception e) {}
    }


    private void turnGpsOff (Context context) {
        if (null == beforeEnable) {
            String str = Settings.Secure.getString (context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            if (null == str) {
                str = "";
            } else {
                String[] list = str.split (",");
                str = "";
                int j = 0;
                for (int i = 0; i < list.length; i++) {
                    if (!list[i].equals (LocationManager.GPS_PROVIDER)) {
                        if (j > 0) {
                            str += ",";
                        }
                        str += list[i];
                        j++;
                    }
                }
                beforeEnable = str;
            }
        }
        try {
            Settings.Secure.putString (context.getContentResolver(),
                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                    beforeEnable);
        } catch(Exception e) {}
    }
}
