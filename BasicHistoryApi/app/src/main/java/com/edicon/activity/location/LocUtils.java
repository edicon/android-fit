package com.edicon.activity.location;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
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
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.fitness.FitnessStatusCodes;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.Calendar;

/**
 * A utility class that is used in both the handset and wearable apps.
 */
public class LocUtils {

    private final static String TAG = "UTIL_LOC";
    public static final long INTERVAL          = 1000 * 10;
    public static final long FASTEST_INTERVAL  = 1000 * 5;

    private LocUtils() {
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

    public static Location getLastLocation( GoogleApiClient mClient ) {
        Location location = null;
        try {
            location = LocationServices.FusedLocationApi.getLastLocation(mClient);
        } catch ( SecurityException e ) {
            e.printStackTrace();
        }
        return location;
    }

    public static LocationRequest createLocationRequest( long interval, long fastestInterval) {
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(interval);
        locationRequest.setFastestInterval(fastestInterval);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return locationRequest;
    }

    public static PendingResult<Status> startLocationUpdates(GoogleApiClient mClient, LocationRequest locationRequest, LocationListener locationListener) {

        PendingResult<Status> pendingResult = null;
        try {
            pendingResult = LocationServices
                    .FusedLocationApi
                    .requestLocationUpdates( mClient, locationRequest, locationListener);
        } catch ( SecurityException e ) {
            e.printStackTrace();
        }
        Log.d(TAG, "Location update started");
        return pendingResult;
    }

    public static void stopLocationUpdates( GoogleApiClient mClient, LocationListener locationListener) {
        LocationServices.FusedLocationApi.removeLocationUpdates( mClient, locationListener);
        Log.d(TAG, "Location update stopped");
    }
}
