package com.edicon.activity.location.android;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.edicon.activity.common.Utils;
import com.google.android.gms.fit.samples.basichistoryapi.BuildConfig;
import com.edicon.activity.common.logger.Log;

import static com.edicon.activity.common.PermUtils.checkMyPermission;

// Google Play Location Service or Android platform location APIs
// http://stackoverflow.com/questions/21397177/finding-location-google-play-location-service-or-android-platform-location-api
// ToDo: Using Google Location API
// http://appus.pro/blog/difference-between-locationmanager-and-google-location-api-services

public class LocationService  {
    private final static String TAG = "Location";

    //The minimum time beetwen updates in milliseconds
    private static final long MIN_TIME_BW_UPDATES               = 0; // 1000 * 60 * 1; // 1 minute
    //The minimum distance to change updates in meters
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES   = 0; // 10 meters

    private final static boolean forceNetwork           = false;
    private static boolean locationServiceAvailable     = false;

    private double longitude, latitude;
    private static Location location, currentBestLocation;
    private static LocationManager locationManager;
    private static LocationService instance             = null;

    /**
     * Singleton implementation
     * @return
     */
    public static LocationService getLocationService(Context context)     {
        if( !checkMyPermission( context ))
            return null;

        if (instance == null) {
            instance = new LocationService(context);
        }
        return instance;
    }

    public boolean getLocationServiceState() {
        return locationServiceAvailable;
    }
    public LocationManager getLocationManager() {
        return locationManager;
    }

    private LocationService( Context context )     {
        initLocationService(context);
        Log.d(TAG, "LocationService created");
    }

    /**
     * Sets up location service after permissions is granted
     */
    // @TargetApi(23)
    private void initLocationService(Context context) {

        if( !checkMyPermission( context ))
            return;

        try   {
            this.longitude = 0.0;
            this.latitude  = 0.0;
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            // Get GPS and network status
            boolean isGPSEnabled        = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled    = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (forceNetwork) isGPSEnabled = false;

            if (!isNetworkEnabled && !isGPSEnabled)    {
                locationServiceAvailable = false;
                Log.w(TAG, "initLocationService: " + "Catnot find location service");
                return;
            }

            locationServiceAvailable = true;
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListener);
                if (locationManager != null)   {
                    currentBestLocation = location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    updateLocation( location );
                }
            }

            if (isGPSEnabled)  {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, locationListener);

                if (locationManager != null)  {
                    currentBestLocation = location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    updateLocation( location );
                }
            }
    } catch (Exception e)  {
            e.printStackTrace();
            Log.e(TAG, "Error creating location service: " + e.getMessage());
        }
    }

    // Define a listener that responds to location updates
    private static LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            updateLocation( location );
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {}

        public void onProviderEnabled(String provider) {
            Log.d(TAG, "onProviderEnabled: " + provider );
        }

        public void onProviderDisabled(String provider) {
            Log.d(TAG, "onProviderDisabled: " + provider );
        }
    };

    public static void removeListener( Context context ) {
        if( !checkMyPermission( context ))
            return;
        locationManager.removeUpdates(locationListener);
    }

    private static void updateLocation( Location location ) {
       // ToDo: Save to local db or firebase
        if( Utils.isBetterLocation( location, currentBestLocation ))
            currentBestLocation = location;

        if(BuildConfig.DEBUG ) {
            Log.d(TAG, "updateLocation: " + location.getLatitude() + ":" + location.getLongitude());
            Log.d(TAG, " --> currentBestLocation: " + currentBestLocation.getLatitude() + ":" + currentBestLocation.getLongitude());
        }
    }
}
