package com.edicon.activity.common;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;

import com.edicon.activity.common.logger.Log;
import com.edicon.activity.common.logger.LogView;
import com.edicon.activity.common.logger.LogWrapper;
import com.edicon.activity.common.logger.MessageOnlyLogFilter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.fitness.FitnessStatusCodes;

import java.util.Calendar;

/**
 * A utility class that is used in both the handset and wearable apps.
 */
public class Utils {

    private Utils() {
    }

    /**
     *  Initialize a custom log class that outputs both to in-app targets and logcat.
     */
    public static void initializeLogging( AppCompatActivity a ) {
        // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView logView = (LogView) a.findViewById(R.id.sample_logview);

        // Fixing this lint error adds logic without benefit.
        //noinspection AndroidLintDeprecation
        logView.setTextAppearance(a, R.style.Log);

        logView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(logView);
    }
    /**
     * Builds a simple hash for a day by concatenating year and day of year together. Note that two
     * {@link java.util.Calendar} inputs that fall on the same day will be hashed to the same
     * string.
     */
    public static String getHashedDay(Calendar day) {
        return day.get(Calendar.YEAR) + "-" + day.get(Calendar.DAY_OF_YEAR);
    }

    // @link: https://developers.google.com/android/guides/api-client
    // Bool to track whether the app is already resolving an error
    public static boolean   mResolvingError            = false;
    public static final int REQUEST_RESOLVE_ERROR      = 1001;
    public static final int REQUEST_OAUTH              = 1002;
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR            = "dialog_error";
    public  static final String STATE_RESOLVING_ERROR   = "resolving_error";

    public static void handleConnectionFailed(AppCompatActivity a, GoogleApiClient mGoogleApiClient, ConnectionResult result ) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                if (result.getErrorCode() == FitnessStatusCodes.NEEDS_OAUTH_PERMISSIONS)
                    result.startResolutionForResult( a, REQUEST_OAUTH);
                else
                    result.startResolutionForResult( a, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            // Show dialog using GoogleApiAvailability.getErrorDialog()
            showErrorDialog(a, result.getErrorCode());
            mResolvingError = true;
        }
    }

    // The rest of this code is all about building the error dialog
    /* Creates a dialog for an error message */
    private static AppCompatActivity errorAppCompatActivity;
    private static void showErrorDialog(AppCompatActivity a, int errorCode) {
        errorAppCompatActivity = a;
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(a.getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {
        public ErrorDialogFragment() { }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            // ToDo: Comment Out for Debugging
            // ((AppCompatActivity) this.getActivity()).onDialogDismissed();
        }
    }

    public static boolean isGooglePlayServicesAvailable( AppCompatActivity a ) {
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(a);
        if (ConnectionResult.SUCCESS == status) {
            return true;
        } else {
            GooglePlayServicesUtil.getErrorDialog(status, a, 0).show();
            return false;
        }
    }
}
