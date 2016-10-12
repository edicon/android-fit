package com.edicon.activity.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import com.edicon.activity.common.PermUtils;
import com.edicon.activity.common.logger.Log;
import com.edicon.activity.location.LocationEntry;
import com.edicon.activity.location.db.LocationDataManager;
import com.google.android.gms.fit.samples.basichistoryapi.BuildConfig;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.UiSettings;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by mglish.hslee on 2016-10-12.
 */

public class MapUtils {

    private final static String TAG ="MAP";

    public static void setDefaultMapUI(GoogleMap map ) {
        UiSettings uiSettings = map.getUiSettings();

        uiSettings.setZoomControlsEnabled( false );
        uiSettings.setCompassEnabled( true );
        uiSettings.setMyLocationButtonEnabled( true );
        uiSettings.setIndoorLevelPickerEnabled( true );
        uiSettings.setMapToolbarEnabled( true );
        uiSettings.setZoomGesturesEnabled( true );
        uiSettings.setScrollGesturesEnabled( true );
        uiSettings.setTiltGesturesEnabled( true );
        uiSettings.setRotateGesturesEnabled( true );
    }

    public static void enableMyLocation( GoogleMap mMap ) {
        try {
            mMap.setMyLocationEnabled(true);
        } catch( SecurityException e ) {
            e.printStackTrace();
        }
    }

    public static void saveLocation(LocationDataManager mDataManager, double latitude, double longitude) {
        Calendar cal = Calendar.getInstance();
        long time = cal.getTimeInMillis();
        cal.setTimeInMillis(time);

        mDataManager.addPoint( new LocationEntry(cal, latitude, longitude));

        if(BuildConfig.DEBUG ) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timeStr = sdf.format(cal.getTime());
            Log.d(TAG, "Location is saved: " + timeStr );
        }
    }
}
