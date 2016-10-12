/*
 * Copyright (C) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.edicon.activity.location.google;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import com.edicon.activity.ActivityApplication;
import com.edicon.activity.common.logger.Log;
import com.edicon.activity.location.db.LocationDataManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;
import java.util.Date;

import static com.edicon.activity.common.PermUtils.MY_ACCESS_FINE_LOCATION;
import static com.edicon.activity.common.Utils.initializeLogging;
import static com.edicon.activity.common.Utils.isGooglePlayServicesAvailable;
import static com.edicon.activity.location.LocUtils.FASTEST_INTERVAL;
import static com.edicon.activity.location.LocUtils.INTERVAL;
import static com.edicon.activity.location.LocUtils.createLocationRequest;
import static com.edicon.activity.location.LocUtils.getLastLocation;
import static com.edicon.activity.location.LocUtils.startLocationUpdates;
import static com.edicon.activity.location.LocUtils.stopLocationUpdates;
import static com.edicon.activity.map.MapUtils.saveLocation;

public class LocationUpdateActivity extends AppCompatActivity {

    public static final String TAG = "GoogleLocation";
    private static final int REQUEST_OAUTH = 1;

    private static GoogleApiClient mClient = null;
    private static String lastUpdateTime;
    private static Location lastLocation, currentLocation;
    private static LocationRequest locationRequest;
    private LocationDataManager mDataManager;

    private static AppCompatActivity thisActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thisActivity = this;
        initializeLogging( thisActivity );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            if (!(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Show an expanation to the user *asynchronously* -- don't block this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_ACCESS_FINE_LOCATION);
                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an app-defined int constant. The callback method gets the  result of the request.
                }
            }
        }

        if (!isGooglePlayServicesAvailable( thisActivity )) {
            finish();
        }

        locationRequest = createLocationRequest( INTERVAL, FASTEST_INTERVAL );
        mClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks( clientConnectionCallbacks )
                .addOnConnectionFailedListener( clientConnectionFailedListener )
                .build();

        setContentView(R.layout.activity_main);
        /*
        tvLocation = (TextView) findViewById(R.id.tvLocation);

        btnFusedLocation = (Button) findViewById(R.id.btnShowLocation);
        btnFusedLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                updateUI();
            }
        });
        */
        mDataManager = ((ActivityApplication) getApplicationContext()).getDataManager();
    }



    private GoogleApiClient.ConnectionCallbacks clientConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected: " + mClient.isConnected());

            lastLocation = getLastLocation( mClient );
            startLocationUpdates( mClient, locationRequest, locationListener);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
    };

    private GoogleApiClient.OnConnectionFailedListener clientConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "Connection failed: " + connectionResult.toString());
        }
    };

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            Log.d(TAG, " --> onLocationChanged");
            currentLocation = location;
            lastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            updateUI();
        }
    };

    private void updateUI() {
        Log.d(TAG, "UI update initiated .............");
        if (null != currentLocation) {
            String lat = String.valueOf(currentLocation.getLatitude());
            String lng = String.valueOf(currentLocation.getLongitude());
        } else {
            Log.d(TAG, "location is null ...............");
        }

        double latitude = currentLocation.getLatitude();
        double longitude = currentLocation.getLongitude();
        saveLocation( mDataManager, latitude, longitude);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mClient.isConnected()) {
            startLocationUpdates( mClient, locationRequest, locationListener);
            Log.d(TAG, "Location update resumed");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates( mClient, locationListener );
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, " --> onStart");

        mClient.connect();
        Log.d(TAG, " --> mClient.connect");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, " --> onStop");

        mClient.disconnect();
        Log.d(TAG, " --> mClient.disconnect");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH && resultCode == RESULT_OK) {
            mClient.connect();
        }
    }
}
