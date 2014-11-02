package ambious.htccarxposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.IntentService;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.stericson.RootTools.RootTools;

import java.util.ArrayList;

/**
 * Created by Elad Avron on 07/07/2014.
 * This helper intent-service runs in the background and handles locally anything the module itself can't handle in the context of the car app.
 * It's called "Killer" because it's original purpose was only to kill other apps.
 */
public class Killer extends IntentService {
    private final String LOG_TAG = "HTCCarModeXposed - (Killer)";
    private static ArrayList<String> _mainAppList;
    ComponentName devAdminReceiver;
    DevicePolicyManager mDPM;
    public static final int KILL_SWITCH = 0;
    public static final int ADD_TO_LIST = 1;
    public static final int LOCK_SCREEN = 2;
    private Handler handler;

    public Killer() {
        super("HTCCarModeXposed(Killer)");
        if (_mainAppList == null) //Initialize the array if it's not already initialized.
            _mainAppList = new ArrayList<String>();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        int action = intent.getIntExtra("action", -1);
        switch (action) {
            case -1:
                Log.e(LOG_TAG, "Killer service received null intent action");
                return;
            case ADD_TO_LIST:
                if (intent.getStringExtra("packageName") != null) {
                    if (intent.getStringExtra("packageName").equals(this.getPackageName())) {
                        Log.i(LOG_TAG, "Skipping from adding self to kill list!");
                        return;
                    }
                    _mainAppList.add(intent.getStringExtra("packageName"));
                    Log.i(LOG_TAG, "Adding package: " + intent.getStringExtra("packageName"));
                    Log.i(LOG_TAG, "Total number of packages listed: " + _mainAppList.size());
                    break;
                } else {
                    Log.e(LOG_TAG, "Received a null package name");
                    return;
                }
            case KILL_SWITCH:
                try {
                    Log.i(LOG_TAG, "Got kill order! Killing " + _mainAppList.size() + " apps!");
                    Log.d(LOG_TAG, "Trying regular method first...");
                    ActivityManager am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
                    for (String packageName : _mainAppList) { //Cycle the apps in the 'kill' list and try to shut them down
                        Log.i(LOG_TAG, "Attempting to shutdown " + packageName + " using regular method.");
                        am.killBackgroundProcesses(packageName);
                    }
                    for (String packageName : _mainAppList) //Cycle the apps in the 'kill' switch and check if they're still up.
                        if (!RootTools.isProcessRunning(packageName)) {
                            Log.i("LOG_TAG", packageName + " closed successfully!");
                            _mainAppList.remove(packageName);
                        }
                    if (_mainAppList.size() > 0) //If any apps in the kill list are still open
                    {
                        Log.w(LOG_TAG, "Some apps could not be closed using the regular method!");
                        //For anything that remains, use the root method
                        SharedPreferences prefFile = getSharedPreferences("xPreferences", MODE_WORLD_READABLE);
                        if (prefFile.getBoolean("kill_root", false)) { //Check if user enabled root-killing
                            for (String packageName : _mainAppList) { //Cycle the remaining apps in the 'kill' list and kill using root method
                                Log.i(LOG_TAG, "Attempting to shutdown " + packageName + " using root method.");
                                RootTools.killProcess(packageName);
                            }
                        } else {
                            Log.w(LOG_TAG, "Some apps could not be closed! User opted out of using root.");
                        }
                    }
                    _mainAppList.clear();
                } catch (Exception ex) {
                    Log.e(LOG_TAG, "Unknown Exception!");
                    ex.printStackTrace();
                    return;
                }
                break;
            case LOCK_SCREEN:
                mDPM = (DevicePolicyManager)this.getSystemService(Context.DEVICE_POLICY_SERVICE);
                devAdminReceiver = new ComponentName(this, ModuleDeviceAdminReceiver.class);
                if (mDPM.isAdminActive(devAdminReceiver))
                {
                    mDPM.lockNow();
                }
                else
                {
                    Log.e(LOG_TAG,"Tried locking screen without Admin privilages!");
                }
                break;
            default:
                Log.e(LOG_TAG, "Unrecognized intent!");
                Log.e(LOG_TAG, "Intent action: " + action);
                return;
        }
    }
}
