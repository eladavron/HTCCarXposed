package ambious.htccarxposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.stericson.RootTools.RootTools;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Elad Avron on 07/07/2014.
 */
public class Killer extends IntentService {
    private final String LOG_TAG = "HTCCarModeXposed(Killer)";
    private static ArrayList<String> _mainAppList;

    public Killer() {
        super("HTCCarModeXposed(Killer)");
        if (_mainAppList == null)
            _mainAppList = new ArrayList<String>();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_RUN)) {
            if (intent.getStringExtra("packageName") != null || intent.getStringExtra("packageName") != null) {
                _mainAppList.add(intent.getStringExtra("packageName"));
                Log.i(LOG_TAG, "Adding package: " + intent.getStringExtra("packageName"));
                Log.i(LOG_TAG, "Total number of packages listed: " + _mainAppList.size());
            }
        } else if (intent.getAction().equals(Intent.ACTION_GET_CONTENT))
        {
            try {
                Log.i(LOG_TAG, "Got kill order! Killing " + _mainAppList.size() + " apps!");
                Log.d(LOG_TAG, "Trying regular method first.");
                ActivityManager am = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
                for (String packageName : _mainAppList) {
                    Log.i(LOG_TAG, "Attempting to shutdown " + packageName + " using regular method.");
                    am.killBackgroundProcesses(packageName);
                }
                for (String packageName : _mainAppList) //Remove apps that closed successfully from the open apps list
                    if (!isAppRunning(packageName)) {
                        Log.i("LOG_TAG", packageName + " closed successfully!");
                        _mainAppList.remove(packageName);
                    }

                if (_mainAppList.size() > 0) //Any apps still open?
                {
                    Log.w(LOG_TAG,"Some apps could not be closed using the regular method!");
                    //For anything that remains, use the root method
                    SharedPreferences prefFile =  getSharedPreferences("xPreferences", MODE_WORLD_READABLE);
                    if (prefFile.getBoolean("kill_root",false)) {
                        for (String packageName : _mainAppList) {
                            Log.i(LOG_TAG, "Attempting to shutdown " + packageName + " using root method.");
                            RootTools.killProcess(packageName);
                        }
                    } else {
                        Log.w(LOG_TAG,"Some apps could not be closed! User opted out of using root.");
                        _mainAppList.clear();
                        return;
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

    /**
     * Checks if the process is running
     * @param packageName the name of the process to checj
     * @return true if it is, false if it isn't
     */
    private boolean isAppRunning(String packageName)
    {
        ActivityManager activityManager = (ActivityManager) this.getSystemService( ACTIVITY_SERVICE );
        List<ActivityManager.RunningAppProcessInfo> procInfos = activityManager.getRunningAppProcesses();
        for(int i = 0; i < procInfos.size(); i++)
        {
            if(procInfos.get(i).processName.equals(packageName))
            {
                return true;
            }
        }
        return false;
    }
}
