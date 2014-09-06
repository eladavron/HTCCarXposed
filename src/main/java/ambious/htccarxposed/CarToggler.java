package ambious.htccarxposed;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

/**
 * This class is a launchable that has not interface.
 * It checks if the car app is running - shuts it down if it is, and starts it if it doesn't.
 * Created by Elad Avron on 04/09/2014.
 */
public class CarToggler extends Activity
{
    private final String LOG_TAG = "HTCCarModeXposed - (Toggler)";
    private SharedPreferences _pref;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        _pref = PreferenceManager.getDefaultSharedPreferences(this);
        if (!_pref.getBoolean("enable_app_icon",true))
            Log.w(LOG_TAG,"Toggler somehow launched with the App Icon settings off!");
        ActivityManager mActivityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        Boolean isRunning = false;
        for (int i=0;i<list.size();i++)
        {
            if (list.get(i).processName.equals("com.htc.AutoMotive"))
                isRunning = true;
        }
        if (!isRunning) {
            Log.i(LOG_TAG, "Car app NOT running.");
            if (_pref.getBoolean("toggler_launch",true)) {
                Log.i(LOG_TAG, "Launching Car app...");
                Intent intent = new Intent();
                intent.setPackage("com.htc.AutoMotive");
                startActivity(intent);
                finish();
            }
            else
            {
                Log.i(LOG_TAG, "Settings say not to launch.");
                finish();
            }
        }
        else if (isRunning)
        {
            Log.i(LOG_TAG, "Car app is running - attempting to shut down...");
            Intent intent = new Intent();
            intent.putExtra("killOrder",true);
            intent.setAction(Intent.ACTION_MAIN);
            intent.setPackage("com.htc.AutoMotive");
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }
}
