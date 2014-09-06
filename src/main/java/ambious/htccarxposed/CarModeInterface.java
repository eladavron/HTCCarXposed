package ambious.htccarxposed;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

public class CarModeInterface extends PreferenceActivity  implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences prefFile;
    private final String LOG_TAG = "HTCCarModeXposed";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.prefs);
        prefFile = getSharedPreferences("xPreferences", MODE_WORLD_READABLE); //Enable the 'world-readable' preference file. This is neccesary because the module can't access the default one.
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        syncListPrefSummary((ListPreference) findPreference("wifi_mode"));
        syncListPrefSummary((ListPreference) findPreference("wifi_exit"));
//        syncListPrefSummary((ListPreference) findPreference("gps_mode"));
//        syncListPrefSummary((ListPreference) findPreference("gps_exit"));
        killBackground(); //Kill any open instances of the car-app so settings are applied next time it's accessed.
    }

    /**
     * The default preference listener. Automatically saves all changed settings to a seperate settings file which is world-readable so the module can read it.
     * (the default settings file is only readable by its own package, and the module technically running on the Car app's namespace).
     */
    private Preference.OnPreferenceChangeListener changeListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference.getClass() == ListPreference.class) {
                int newValueInt = Integer.parseInt(newValue.toString());
                preference.setSummary(((ListPreference) preference).getEntries()[newValueInt]);
                prefFile.edit()
                        .putInt(preference.getKey(), newValueInt)
                        .commit();
            } else if (preference.getClass() == CheckBoxPreference.class)
                prefFile.edit()
                        .putBoolean(preference.getKey(), (Boolean) newValue)
                        .commit();
            if (preference.getKey().equals("allow_pulldown") && !(Boolean) newValue)
                ((CheckBoxPreference)findPreference("allow_multitasking")).setChecked(false);
            if (preference.getKey().equals("kill_apps") && (Boolean) newValue == false)
                ((CheckBoxPreference) findPreference("kill_root")).setChecked(false);
            killBackground(); //If changes were made, they'll only apply if the car app is restarted.
            return true;
        }
    };

    /**
     * Sets the summary of a ListPreference to its value
     */
    private void syncListPrefSummary(ListPreference preference)
    {
        try {
            int newValueInt = Integer.parseInt(preference.getValue());
            CharSequence newSummary = preference.getEntries()[newValueInt];
            preference.setSummary(newSummary);
        }
        catch (NumberFormatException ex)
        {
            Log.e(LOG_TAG,"Value of \"" + preference.getKey() + "\" is not numerical!");
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        Preference preference = findPreference(s);
        if (preference.getClass() == ListPreference.class) {
            int newValueInt = Integer.parseInt(sharedPreferences.getString(s,null));
            preference.setSummary(((ListPreference) preference).getEntries()[newValueInt]);
            prefFile.edit()
                    .putInt(s, newValueInt)
                    .commit();
        } else if (preference.getClass() == CheckBoxPreference.class)
            prefFile.edit()
                    .putBoolean(s, sharedPreferences.getBoolean(s,false))
                    .commit();
        if (s.equals("allow_pulldown") && !sharedPreferences.getBoolean(s,true))
            ((CheckBoxPreference)findPreference("allow_multitasking")).setChecked(false);
        if (s.equals("kill_apps") && sharedPreferences.getBoolean(s,false) == false)
            ((CheckBoxPreference) findPreference("kill_root")).setChecked(false);
        if (s.equals("kill_root") && sharedPreferences.getBoolean(s,false))
        {
            //Check for root privilages
            if (!RootTools.isAccessGiven()) {
                Log.e(LOG_TAG, "Couldn't get root privileges!");
                ((CheckBoxPreference)preference).setChecked(false);
                Toast.makeText(getApplicationContext(), getText(R.string.root_failed), Toast.LENGTH_LONG).show();
                return;
            }
        }

        /*
        //This is temporarily removed due to it causing the module not to see the app interface

        if (s.equals("enable_app_icon"))
        {
            if  (sharedPreferences.getBoolean(s,true))
            {
                //Re-adds the icon to the launcher
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            } else {
                //Removes the app Icon from the launcher - it's still accessibly from the Xposed Module Installer.
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        */

        if (s.equals("enable_toggler"))
        {
            ComponentName cn = new ComponentName(this, "ambious.htccarxposed.CarToggler");
            if (cn == null)
            {
                Log.w(LOG_TAG,"ComponentName was null!");
                return;
            }
            if  (sharedPreferences.getBoolean(s,true))
            {
                //Re-adds the toggler icon to the launcher
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
            } else {
                //Removes the toggler Icon from the launcher - it's still accessibly from the Xposed Module Installer.
                PackageManager p = getPackageManager();
                p.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        killBackground(); //If changes were made, they'll only apply if the car app is restarted.
    }

    /**
     * Kills the car app in the background
     */
    private void killBackground() {
        ActivityManager mActivityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        mActivityManager.killBackgroundProcesses("com.htc.AutoMotive");
    }

}
