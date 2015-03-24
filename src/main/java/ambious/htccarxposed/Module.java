package ambious.htccarxposed;

/**
 * Created by Elad on 01/06/2014.
 */

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
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
    final String LOG_TAG = "htccarxposed";
    final int D = 0;
    final int I = 1;
    final int W = 2;
    final int E = 3;
    final int VERSION_33 = 330001270;
    final int VERSION_34 = 342628413;
    final int VERSION_75 =  750160455;
    int CAR_VERSION;
    String methodName, className, fieldName;
    static boolean logger = false;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        xSharedPreferences = new XSharedPreferences("ambious.htccarxposed", "xPreferences"); //Load the user preferences file called 'xPreferences' - since it's world-readable while the default preference file isn't.
        logger = xSharedPreferences.getBoolean("enable_logging", false);
        CAR_VERSION = xSharedPreferences.getInt("carVersion", -1);
        if (CAR_VERSION == -1)
            Logger(E,"Car Version not successfully retrieved!");

        if (!(lpparam.packageName.equals("com.htc.AutoMotive") || lpparam.processName.equals("android"))) //Make sure we only operate within the car-app and other required services //
            return;

        /**
         * Multitasking - this part intercepts "Recent Apps" requests, and if "Autmotive" (carmode) is set to "true", it over-rides it to "false", thus allowing multi-tasking.
         * Only relevant in KitKat!
         */
        Logger(D,"Car Version = " + CAR_VERSION);
        if (Build.VERSION.SDK_INT < 20 && lpparam.packageName.equals("android")) {
            Logger(I,"Hooked into Android Policy!");
            Class<?> HandleRecentAppsRunnable = findClass("com.android.internal.policy.impl.PhoneWindowManager.HandleRecentAppsRunnable",lpparam.classLoader);
            findAndHookMethod(HandleRecentAppsRunnable, "run", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    xSharedPreferences.reload();
                    boolean allow_multitasking = xSharedPreferences.getBoolean("allow_multitasking", true); //Check user settings - default is true. Has to be here otherwise it'll be set on startup.
                    if (getBooleanField(getSurroundingThis(param.thisObject),"mAutoMotiveEnabled") && allow_multitasking) //Only intercepts if "allow multitasking" is set and car mode is active.
                    {
                        Logger(D,"Intercepted Recent Apps request!");
                        setBooleanField(getSurroundingThis(param.thisObject), "mAutoMotiveEnabled", false);
                    }
                }
            });
            return;
        }
        else if (lpparam.processName.equals("android")) {
            Logger(I,"Hooked into Android Policy!");
            Class<?> interceptKeyBeforeDispatching = findClass("com.android.internal.policy.impl.PhoneWindowManager", lpparam.classLoader);
            findAndHookMethod(interceptKeyBeforeDispatching, "interceptKeyBeforeDispatching", "android.view.WindowManagerPolicy$WindowState", KeyEvent.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    xSharedPreferences.reload();
                    boolean allow_multitasking = xSharedPreferences.getBoolean("allow_multitasking", true); //Check user settings - default is true. Has to be here otherwise it'll be set on startup.
                    if (((KeyEvent)param.args[1]).getKeyCode() == 187 && getBooleanField(param.thisObject,"mAutoMotiveEnabled") && allow_multitasking) //Only intercepts if "allow multitasking" is set and car mode is active.
                    {
                        Logger(D,"Intercepted Recent Apps request!");
                        setBooleanField(param.thisObject, "mAutoMotiveEnabled", false);
                    }
                }
            });
        }

        /**
         * Anything beyond this point is on com.htc.Automotive
         */

        Logger(I, "HTC Automotive Loaded!");
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
                Logger(D,"Context set to OnCreate: " + _this.toString());

                //Startup mods
                if (wifi_mode != 0) {
                    WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                    switch (wifi_mode) {
                        case 1:
                            wifiManager.setWifiEnabled(true);
                            Logger(I, "Turning WIFI on!");
                            break;
                        case 2:
                            wifiManager.setWifiEnabled(false);
                            Logger(I, "Turning WIFI off!");
                            break;
                    }
                }

                /**
                 * GPS settings on startup
                 */
                if (gps_mode != 0)
                {
                    final Intent intent = new Intent("com.htc.settings.action.SET_GPS_ENABLED");
                    boolean newStatus;
                    switch (gps_mode)
                    {
                        case 1:
                            newStatus = true;
                            break;
                        case 2:
                            newStatus = false;
                            break;
                        default:
                            Logger(E,"Unrecognized GPS mode: " + gps_mode);
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
                    Logger(I,"Killing app by request of toggler.");
                    switch (CAR_VERSION) {
                        case VERSION_33:
                            methodName = "finishCarmode";
                            break;
                        case VERSION_34:
                        case VERSION_75:
                        default:
                            methodName = "b";
                    }
                    callMethod(param.thisObject, methodName);
                    return;
                }
            }
        });



        /**
         * Enable statusbar - also displays the 'recent-apps' while not actually enabling it.
         */
        final boolean allow_pulldown = xSharedPreferences.getBoolean("allow_pulldown", true); //Check user settings - default is true
        if (allow_pulldown) {
            switch(CAR_VERSION) {
                case VERSION_33:
                    methodName = "setStatusBarLocked";
                    break;
                case VERSION_34:
                    methodName = "i";
                    break;
                case VERSION_75:
                default:
                    methodName = "j";
            }
            findAndHookMethod(mainClass, methodName, boolean.class, new XC_MethodReplacement() {

                /**
                 * Intercepts the method and completely replaces it.
                 */
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Logger(I,"Statusbar released.");
                    return null;
                }
            });
        }

        /**
         * Home button - currently works by intercepting and bypassing a method called 'enableUiMode', but it may break other things.
         */

        boolean capture_home = xSharedPreferences.getBoolean("capture_home", true);
        if (!capture_home) {
            switch(CAR_VERSION)
            {
                case VERSION_33:
                    methodName = "enableUiMode";
                    break;
                case VERSION_34:
                case VERSION_75:
                default:
                    methodName = "e";
            }
            findAndHookMethod(mainClass, methodName, new XC_MethodReplacement() {
                /**
                 * Intercepts the method and completely replaces it.
                 */
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Logger(I,"Home button released.");
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
            switch (CAR_VERSION){
                case VERSION_33:
                    methodName = "acquireWakeLock";
                    break;
                case VERSION_34:
                    methodName = "f";
                    break;
                case VERSION_75:
                default:
                    methodName = "g";
            }
            findAndHookMethod(mainClass, methodName, boolean.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Logger(I,"Wakelock released.");
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
                    Logger(D,"Intercepted gesture handler!");
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
                            Logger(E, "Unrecognized gesture setting: " + xSharedPreferences.getInt("gesture_override",0));
                            return null;
                    }
                    try {
                        _mainActivity.startActivityForResult(_intent, _mainActivity.getTaskId());
                    } catch (ActivityNotFoundException notFound)
                    {
                        Logger(E,"Google Now either not installed or not configured correctly!");
                        Logger(E,notFound.getStackTrace().toString());
                    } catch (Exception e)
                    {
                        Logger(E, "Failed to handle intent!\nIntent action: " + _intent.getAction() + "\nIntent class: " + _intent.getClass().getName());
                    }
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
                        Logger(I,"Sending kill command.");
                        callService(Killer.KILL_SWITCH);
                    }
                    if (wifi_exit != 0) {
                        WifiManager wifiManager = (WifiManager) ((Context) param.thisObject).getSystemService(Context.WIFI_SERVICE);
                        if (wifi_exit == 1) {
                            wifiManager.setWifiEnabled(true);
                            Logger(I,"Turning WIFI on!");
                        } else if (wifi_exit == 2) {
                            wifiManager.setWifiEnabled(false);
                            Logger(I, "Turning WIFI off!");
                        }
                    }

                    if (turnoff_screen)
                    {
                        Logger(I,"Attempting to lock screen...");
                        callService(Killer.LOCK_SCREEN);
                    }

                    /**
                     * GPS Settings on exit
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
                            Logger(E, "Unrecognized GPS mode: " + gps_exit);
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
            switch (CAR_VERSION)
            {
                case VERSION_33:
                    methodName = "safeStartActivity";
                    className = "com.htc.AutoMotive.util.Utils";
                    break;
                case VERSION_34:
                    className = "com.htc.AutoMotive.util.n";
                    methodName = "a";
                    break;
                case VERSION_75:
                default:
                    className = "com.htc.AutoMotive.util.r";
                    methodName = "a";
            }
            findAndHookMethod(className, lpparam.classLoader, methodName, Context.class, Intent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    super.beforeHookedMethod(param);
                    Intent _intent = (Intent) param.args[1]; //Add the new intent to list
                    Logger(D,"Adding " + _intent.getComponent().getPackageName() + " to intent array.");
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
                    switch (CAR_VERSION)
                    {
                        case VERSION_33:
                            fieldName = "bIsLeaveDialogShowing";
                            methodName = "finishCarmode";
                            break;
                        case VERSION_34:
                            fieldName = "G";
                            methodName = "b";
                            break;
                        default:
                        case VERSION_75:
                            fieldName = "H";
                            methodName = "b";
                    }
                    if (getBooleanField(param.thisObject, fieldName)) { //Intercepts exit dialog
                        Logger(I,"Bypassing exit dialog...");
                        callMethod(param.thisObject, methodName);
                    }
                }
            });
        }
    }

    private void callService(int action, String... args)
    {
        try {
            Intent newIntent = new Intent();
            newIntent.putExtra("action", action);
            newIntent.setComponent(new ComponentName("ambious.htccarxposed", "ambious.htccarxposed.Killer"));
            if (action == Killer.ADD_TO_LIST)
                newIntent.putExtra(args[0], args[1]);
            if (action == Killer.LOGGER)
                newIntent.putExtra("message", args[0]);
            _mainContext.startService(newIntent);
        }
        catch (Exception er)
        {
            Log.e(LOG_TAG,"Error launching service!");
        }
    }

    private void Logger(int type, String message)
    {
        if (Build.VERSION.SDK_INT < 20)
            XposedBridge.log(LOG_TAG + ": " + message);
        else
        {
            switch(type)
            {
                case D:
                    Log.d(LOG_TAG,message);
                    break;
                case I:
                    Log.i(LOG_TAG,message);
                    break;
                case W:
                    Log.w(LOG_TAG,message);
                    break;
                case E:
                    Log.e(LOG_TAG,message);
                    break;
                default:
                    Log.d(LOG_TAG,"Unrecognized error type: " + message);
            }
        }
        if(_mainContext != null && logger)
            callService(Killer.LOGGER,message);
    }
}