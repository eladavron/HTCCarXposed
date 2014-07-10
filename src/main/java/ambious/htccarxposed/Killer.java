package ambious.htccarxposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.stericson.RootTools.RootTools;

import java.util.ArrayList;

/**
 * Created by Elad Avron on 07/07/2014.
 * This helper intent-service runs in the background and handles locally anything the module itself can't handle in the context of the car app.
 * It's called "Killer" because it's original purpose was only to kill other apps.
 */
public class Killer extends IntentService {
    private final String LOG_TAG = "HTCCarModeXposed(Killer)";
    private static ArrayList<String> _mainAppList;

    public Killer() {
        super("HTCCarModeXposed(Killer)");
        if (_mainAppList == null) //Initialize the array if it's not already initialized.
            _mainAppList = new ArrayList<String>();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_RUN)) { //ACTION_RUN is the intent used to run the 'kill switch'
            if (intent.getStringExtra("packageName") != null || intent.getStringExtra("packageName") != null) {
                _mainAppList.add(intent.getStringExtra("packageName"));
                Log.i(LOG_TAG, "Adding package: " + intent.getStringExtra("packageName"));
                Log.i(LOG_TAG, "Total number of packages listed: " + _mainAppList.size());
            }
        }
        else if (intent.getAction().equals(Intent.ACTION_GET_CONTENT)) //ACTION_GET_CONTENT is the intent used to add apps to the 'kill' list.
        {
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
                    Log.w(LOG_TAG,"Some apps could not be closed using the regular method!");
                    //For anything that remains, use the root method
                    SharedPreferences prefFile =  getSharedPreferences("xPreferences", MODE_WORLD_READABLE);
                    if (prefFile.getBoolean("kill_root",false)) { //Check if user enabled root-killing
                        for (String packageName : _mainAppList) { //Cycle the remaining apps in the 'kill' list and kill using root method
                            Log.i(LOG_TAG, "Attempting to shutdown " + packageName + " using root method.");
                            RootTools.killProcess(packageName);
                        }
                    } else {
                        Log.w(LOG_TAG,"Some apps could not be closed! User opted out of using root.");
                    }
                }
                _mainAppList.clear();
            }
            catch (Exception ex)
            {
                Log.e(LOG_TAG,"Unknown Exception!");
                ex.printStackTrace();
                return;
            }
        }
        else
        {
            Log.e(LOG_TAG, "Unrecognized intent!");
            Log.e(LOG_TAG, "Intent action: " + intent.getAction());
            Log.e(LOG_TAG, "Intent extra: " + intent.getStringExtra("packageName"));
        }
    }
}
