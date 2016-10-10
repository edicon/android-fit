package com.edicon.activity.common;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;

import com.edicon.activity.fit.HistoryActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fitness.FitnessStatusCodes;

import java.util.Calendar;

/**
 * A utility class that is used in both the handset and wearable apps.
 */
public class Utils {

    private Utils() {
    }

    /**
     * Builds a simple hash for a day by concatenating year and day of year together. Note that two
     * {@link java.util.Calendar} inputs that fall on the same day will be hashed to the same
     * string.
     */
    public static String getHashedDay(Calendar day) {
        return day.get(Calendar.YEAR) + "-" + day.get(Calendar.DAY_OF_YEAR);
    }

    private static final int TWO_MINUTES = 1000 * 60 * 2;
    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    public static boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
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
}
