package ambious.htccarxposed;

/**
 * Created by Elad on 01/06/2014.
 */

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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
        if (!(lpparam.packageName.equals("com.htc.AutoMotive") || lpparam.packageName.equals("android"))) //Make sure we only operate within the car-app and other required services // || lpparam.packageName.equals("com.htc.HTCSpeaker"
            return;

        /**
         * Multitasking - this part intercepts "Recent Apps" requests, and if "Autmotive" (carmode) is set to "true", it over-rides it to "false", thus allowing multi-tasking.
         */
        if (lpparam.packageName.equals("android")) {
            XposedBridge.log("htccarxposed: Hooked into Android Policy!");
            Class<?> HandleRecentAppsRunnable = findClass("com.android.internal.policy.impl.PhoneWindowManager.HandleRecentAppsRunnable",lpparam.classLoader);
            findAndHookMethod(HandleRecentAppsRunnable, "run", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    xSharedPreferences.reload();
                    boolean allow_multitasking = xSharedPreferences.getBoolean("allow_multitasking", true); //Check user settings - default is true. Has to be here otherwise it'll be set on startup.
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

        final Class<?> mainClass = findClass("com.htc.AutoMotive.carousel.MainActivity", lpparam.classLoader);

        /**
         * Startup Action
         */
        final int wifi_mode = xSharedPreferences.getInt("wifi_mode", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        final int gps_mode = xSharedPreferences.getInt("gps_mode", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        findAndHookMethod(mainClass, "onCreate", Bundle.class, new XC_MethodHook() {
            /**
             * Works by intercepting the startup method and adding code after it runs
             */
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                //Set global context to the car app
                Context _this = (Context) param.thisObject; //Gets the active app instance
                _mainContext = _this;
                XposedBridge.log("htccarxposed: Context set to OnCreate: " + _this.toString());

                //Startup mods
                if (wifi_mode != 0) {
                    WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                    if (wifi_mode == 1) {
                        wifiManager.setWifiEnabled(true);
                        XposedBridge.log("htccarxposed: Turning WIFI on!");
                    } else if (wifi_mode == 2) {
                        wifiManager.setWifiEnabled(false);
                        XposedBridge.log("htccarxposed: Turning WIFI off!");
                    }
                }
                    /*
                    //GPS settings on startup
                    */
                if (gps_mode != 0)
                {
                    final Intent intent = new Intent("com.htc.settings.action.SET_GPS_ENABLED");
                    boolean newStatus;
                    if (gps_mode == 1)
                        newStatus = true;
                    else if (gps_mode == 2)
                        newStatus = false;
                    else {
                        XposedBridge.log("htccarxposed: Unrecognized GPS mode: " + gps_mode);
                        return;
                    }
                    intent.putExtra("extra_enabled", newStatus);
                    _mainContext.sendBroadcast(intent);
                }
            }
        });


        /**
         * This adds an intent handler for the car app - if it detects a 'shutdown' intent it will, of course, shut it down.
         */
        findAndHookMethod(mainClass, "onNewIntent", Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                Intent _intent = (Intent) param.args[0];
                if (_intent.getBooleanExtra("killOrder",false))
                {
                    XposedBridge.log("htccarxposed: Killing app by request of toggler");
                    callMethod(param.thisObject, "finishCarmode");
                    return;
                }
            }
        });



        /**
         * Enable statusbar - also displays the 'recent-apps' while not actually enabling it.
         */
        final boolean allow_pulldown = xSharedPreferences.getBoolean("allow_pulldown", true); //Check user settings - default is true
        if (allow_pulldown) {
            findAndHookMethod(mainClass, "setStatusBarLocked", boolean.class, new XC_MethodReplacement() {

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

        boolean capture_home = xSharedPreferences.getBoolean("capture_home", true);
        if (!capture_home) {
            findAndHookMethod(mainClass, "enableUiMode", new XC_MethodReplacement() {
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
            findAndHookMethod(mainClass, "acquireWakeLock", boolean.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("htccarxposed: Wakelock released.");
                    return null;
                }
            });
        }

        /**
         * 3-Dot tap gesture override
         */
        if (xSharedPreferences.getInt("gesture_override",1) != 0)
            findAndHookMethod("com.htc.AutoMotive.util.SystemGestureService",lpparam.classLoader,"onHandleIntent",Intent.class,new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    XposedBridge.log("htccarxposed: Intercepted gesture handler!");
                    Activity _mainActivity = (Activity) _mainContext;
                    Intent _intent = new Intent();
                    switch (xSharedPreferences.getInt("gesture_override",1))
                    {
                        case 1: //Google's Hands-Free
                            _intent.setAction(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
                            break;
                        case 2: //Google Now
                            _intent.setClassName("com.google.android.googlequicksearchbox","com.google.android.googlequicksearchbox.VoiceSearchActivity");
                            break;
                        case 3: //Custom App
                            String packageName = xSharedPreferences.getString("gesture_package",null);
                            String intentAction = xSharedPreferences.getString("gesture_action", null);
                            if (packageName != null) {
                                _intent.setPackage(packageName);
                                _intent.setAction(intentAction);
                            }
                            break;
                        default:
                            XposedBridge.log("htccarxposed: Unrecognized gesture setting: " + xSharedPreferences.getInt("gesture_override",0));
                            return null;
                    }
                    _mainActivity.startActivityForResult(_intent,_mainActivity.getTaskId());
                    return null;
                }
            });

        /**
         * Exit Wi-Fi Action
         */
        final int wifi_exit = xSharedPreferences.getInt("wifi_exit", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        final int gps_exit = xSharedPreferences.getInt("gps_exit", 0); //Read user preference. 0 = don't change (default), 1 = turn on, 2 = turn off
        final boolean kill_apps = xSharedPreferences.getBoolean("kill_apps",false);
        final boolean turnoff_screen = xSharedPreferences.getBoolean("lock_screen",false); //Checks whether to turn off screen on exit.
        if (wifi_exit != 0 || gps_exit != 0 || kill_apps || turnoff_screen)
            findAndHookMethod(mainClass, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    if (kill_apps) {
                        XposedBridge.log("htccarxposed: Sending kill command.");
                        callService(Killer.KILL_SWITCH);
                    }
                    if (wifi_exit != 0) {
                        WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                        if (wifi_exit == 1) {
                            wifiManager.setWifiEnabled(true);
                            XposedBridge.log("htccarxposed: Turning WIFI on!");
                        } else if (wifi_exit == 2) {
                            wifiManager.setWifiEnabled(false);
                            XposedBridge.log("htccarxposed: Turning WIFI off!");
                        }
                    }

                    if (turnoff_screen)
                    {
                       XposedBridge.log("htccarxposed: Attempting to lock screen...");
                       callService(Killer.LOCK_SCREEN);
                    }

                    /*
                    //GPS Settings on exit
                    */

                    if (gps_exit != 0)
                    {
                        final Intent intent = new Intent("com.htc.settings.action.SET_GPS_ENABLED");
                        boolean newStatus;
                        if (gps_exit == 1)
                            newStatus = true;
                        else if (gps_exit == 2)
                            newStatus = false;
                        else {
                            XposedBridge.log("htccarxposed: Unrecognized GPS mode: " + gps_exit);
                            return;
                        }
                        intent.putExtra("extra_enabled", newStatus);
                        _mainContext.sendBroadcast(intent);
                    }
                }
            });

        /**
         * Log and kill all apps opened during session
         */
        if (kill_apps) {
            findAndHookMethod("com.htc.AutoMotive.util.Utils", lpparam.classLoader, "safeStartActivity", Context.class, Intent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Intent _intent = (Intent) param.args[1]; //Add the new intent to list
                    XposedBridge.log("htccarxposed: adding " + _intent.getComponent().getPackageName() + " to intent array.");
                    if (!_intent.getComponent().getPackageName().equals("ambious.htccarxposed"))
                        callService(Killer.ADD_TO_LIST,"packageName",_intent.getComponent().getPackageName());
                }
            });
        }

        /**
         * Circumvent 'exit' dialog'
         */
        final boolean bypassExitDialog = xSharedPreferences.getBoolean("bypassExitDialog", true);
        if (bypassExitDialog) {
            findAndHookMethod(mainClass, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
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