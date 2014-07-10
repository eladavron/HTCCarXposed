package ambious.htccarxposed;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

import com.stericson.RootTools.RootTools;

public class CarModeInterface extends PreferenceActivity {

    private SharedPreferences prefFile;
    private final String LOG_TAG = "HTCCarModeXposed";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);

        /**
         * Binds all preferences to the default listener. There's probably a way to do it automatically, but I got lazy
         */

        prefFile = getSharedPreferences("xPreferences", MODE_WORLD_READABLE);
        findPreference("allow_pulldown").setOnPreferenceChangeListener(changeListener);
        findPreference("allow_multitasking").setOnPreferenceChangeListener(changeListener);
        findPreference("capture_home").setOnPreferenceChangeListener(changeListener);
        findPreference("bypassExitDialog").setOnPreferenceChangeListener(changeListener);
        findPreference("wifi_mode").setOnPreferenceChangeListener(changeListener);
        findPreference("gps_mode").setOnPreferenceChangeListener(changeListener);
        findPreference("wifi_exit").setOnPreferenceChangeListener(changeListener);
        findPreference("gps_exit").setOnPreferenceChangeListener(changeListener);
        findPreference("kill_apps").setOnPreferenceChangeListener(changeListener);
        findPreference("kill_root").setOnPreferenceChangeListener(changeListener);
        syncListPrefSummary((ListPreference) findPreference("wifi_mode"));
        syncListPrefSummary((ListPreference) findPreference("gps_mode"));
        syncListPrefSummary((ListPreference) findPreference("wifi_exit"));
        syncListPrefSummary((ListPreference) findPreference("gps_exit"));
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
            } else if (preference.getClass() == CheckBoxPreference.class) {
                prefFile.edit()
                        .putBoolean(preference.getKey(), (Boolean) newValue)
                        .commit();
            }
            if (preference.getKey().equals("allow_pulldown") && !(Boolean) newValue)
            {
                ((CheckBoxPreference)findPreference("allow_multitasking")).setChecked(false);
            }
            if (preference.getKey().equals("kill_root") && (Boolean) newValue)
            {
                //Check for root privilages
                if (!RootTools.isAccessGiven()) {
                    Log.e(LOG_TAG, "Couldn't get root privileges!");
                    Toast.makeText(getApplicationContext(), getText(R.string.root_failed), Toast.LENGTH_LONG).show();
                    ((CheckBoxPreference) preference).setChecked(false);
                }
            }
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

    /**
     * Kills the car app in the background
     */
    private void killBackground() {
        ActivityManager mActivityManager = (ActivityManager) getApplicationContext().getSystemService(Context.ACTIVITY_SERVICE);
        mActivityManager.killBackgroundProcesses("com.htc.AutoMotive");
    }
}
