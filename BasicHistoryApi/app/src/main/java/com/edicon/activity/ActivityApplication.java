package com.edicon.activity;

import android.app.Application;

import com.edicon.activity.location.LocationDataManager;
import com.edicon.activity.location.db.LocationDbHelper;

/**
 * The {@link android.app.Application} class for the handset app.
 */
public class ActivityApplication extends Application {

    private LocationDataManager mDataManager;

    @Override
    public void onCreate() {
        super.onCreate();
        LocationDbHelper dbHelper = new LocationDbHelper(getApplicationContext());
        mDataManager = new LocationDataManager(dbHelper);
    }

    /**
     * Returns an instance of {@link com.edicon.activity.location.LocationDataManager}.
     */
    public final LocationDataManager getDataManager() {
        return mDataManager;
    }
}
