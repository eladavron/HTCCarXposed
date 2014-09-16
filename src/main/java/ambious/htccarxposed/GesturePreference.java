package ambious.htccarxposed;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;

/**
 * The following class implements ListPreference to allow us to use the 'custom' option to launch the activity picker
 */
public class GesturePreference extends ListPreference {
    private int mClickedDialogEntryIndex;

    private Context mContext;

    public GesturePreference(Context ctxt) {
        this(ctxt, null);
    }

    public GesturePreference(Context ctxt, AttributeSet attrs) {
        super(ctxt, attrs);
        mContext = ctxt;
        setNegativeButtonText(ctxt.getString(android.R.string.cancel));
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        if (getEntries() == null || getEntries() == null) {
            throw new IllegalStateException(
                    "ListPreference requires an entries array and an entryValues array.");
        }

        mClickedDialogEntryIndex = findIndexOfValue(getValue());
        builder.setSingleChoiceItems(getEntries(), mClickedDialogEntryIndex,
                new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int which) {
                        //3 is the index of the 'custom' box. Change accordingly if you add more options.
                        if(which == 3){
                            // Show AlertDialog
                            CarModeInterface.showSelector((Activity)mContext);
                        }
                        // Save preference and close dialog
                        mClickedDialogEntryIndex = which;
                        GesturePreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();

                    }
                });

        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {

        CharSequence[] mEntryValues = getEntryValues();

        if (positiveResult && mClickedDialogEntryIndex >= 0 && mEntryValues != null) {
            String value = mEntryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }
}