package com.edicon.activity;

import com.edicon.activity.fit.FitHistory;
import com.edicon.activity.location.LocationEntry;
import com.edicon.activity.location.android.LocationService;
import com.edicon.activity.location.db.LocationDataManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.fit.samples.basichistoryapi.BuildConfig;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.edicon.activity.common.PermUtils.MY_ACCESS_FINE_LOCATION;
import static com.edicon.activity.common.Utils.REQUEST_OAUTH;
import static com.edicon.activity.common.Utils.initializeLogging;
import static com.edicon.activity.common.Utils.isGooglePlayServicesAvailable;
import static com.edicon.activity.location.Constants.USE_GOOGLE_LOC_API;
import static com.edicon.activity.location.LocUtils.FASTEST_INTERVAL;
import static com.edicon.activity.location.LocUtils.INTERVAL;
import static com.edicon.activity.location.LocUtils.createLocationRequest;
import static com.edicon.activity.location.LocUtils.getLastLocation;
import static com.edicon.activity.location.LocUtils.startLocationUpdates;
import static com.edicon.activity.location.LocUtils.stopLocationUpdates;
import static com.edicon.activity.map.MapUtils.enableMyLocation;
import static com.edicon.activity.map.MapUtils.saveLocation;
import static com.edicon.activity.map.MapUtils.setDefaultMapUI;

/**
 * Created by hslee on 2016-10-10.
 */

public class TrackingActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        DatePickerDialog.OnDateSetListener {

    private static final String TAG = "TrackingActivity";
    public static final int BOUNDING_BOX_PADDING_PX = 50;
    private TextView mSelectedDateText;

    // Google API
    private static GoogleApiClient mClient = null;

    // Location API
    private static String lastUpdateTime;
    private static Location lastLocation, currentLocation;
    private static LocationRequest locationRequest;
    private LocationDataManager locDataManager;

    // Map API
    private GoogleMap mMap;

    private static AppCompatActivity thisActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tracking_demo);

        thisActivity = this;
        initializeLogging( thisActivity );
        locDataManager = ((ActivityApplication) getApplicationContext()).getDataManager();

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

        // Location API: Android or GoogleAPI
        if( USE_GOOGLE_LOC_API ) {
            locationRequest = createLocationRequest(INTERVAL, FASTEST_INTERVAL);
            mClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(clientConnectionCallbacks)
                    .addOnConnectionFailedListener(clientConnectionFailedListener)
                    .build();
        } else {
            LocationService locationService = LocationService.getLocationService( this );
        }

        // Google Map
        mSelectedDateText = (TextView) findViewById(R.id.selected_date);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private GoogleApiClient.ConnectionCallbacks clientConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected: " + mClient.isConnected());

            currentLocation = lastLocation = getLastLocation( mClient );
            if(BuildConfig.DEBUG && lastLocation != null )
                Log.d(TAG, "lastLocation: " + lastLocation.getLatitude() + "/" + lastLocation.getLongitude());

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
            Log.d(TAG, " --> onLocationChanged: " + location.getLatitude() + "/" + location.getLongitude());

            lastLocation = currentLocation = location;
            lastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            updateUI( location );
        }
    };

    private void updateUI( Location loc ) {
        if( loc == null )
            return;

        double latitude = loc.getLatitude();
        double longitude = loc.getLongitude();
        saveLocation( locDataManager, latitude, longitude);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;

        setDefaultMapUI( mMap );
        enableMyLocation( mMap );
        setMapListener();

        if(BuildConfig.DEBUG && lastLocation == null ) {
            LatLng sydney = new LatLng(-34, 151);
            gotoLoc(mMap, sydney, "Syndey");
        } else {
            LatLng latLng = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
            gotoLoc(mMap, latLng, "Last Location");
        }

        if( fitHistory == null )
            fitHistory = new FitHistory(thisActivity, mMap );
    }

    private void gotoLoc( GoogleMap map, LatLng latLng, String title) {
        map.addMarker(new MarkerOptions().position(latLng).title(title));
        map.moveCamera(CameraUpdateFactory.newLatLng(latLng));
    }

    public void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        new DatePickerDialog( TrackingActivity.this, TrackingActivity.this,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private FitHistory fitHistory;
    @Override
    public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
        // the following if-clause is to get around a bug that causes this callback to be called
        // twice
        if (view.isShown()) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, monthOfYear);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            String date = DateUtils.formatDateTime(this, calendar.getTimeInMillis(), DateUtils.FORMAT_SHOW_DATE);
            mSelectedDateText.setText(getString(R.string.showing_for_date, date));

            if( false )
                showTrack(calendar);
            else {
                if( fitHistory == null )
                    fitHistory = new FitHistory(thisActivity, mMap );
                fitHistory.startTrackingDataTask( calendar );
            }
        }
    }

    /**
     * An {@link android.os.AsyncTask} that is responsible for getting a list of {@link
     * com.edicon.activity.location.LocationEntry} objects for a given day and
     * building a track from those points. In addition, it sets the smallest bounding box for the
     * map that covers all the points on the track.
     */
    private void showTrack(Calendar calendar) {
        new AsyncTask<Calendar, Void, Void>() {

            private List<LatLng> coordinates;
            private LatLngBounds bounds;

            @Override
            protected Void doInBackground(Calendar... params) {
                LocationDataManager dataManager = ((ActivityApplication) getApplicationContext()).getDataManager();
                List<LocationEntry> entries = dataManager.getPoints(params[0]);
                if (entries != null && !entries.isEmpty()) {
                    coordinates = new ArrayList<LatLng>();
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                    for (LocationEntry entry : entries) {
                        LatLng latLng = new LatLng(entry.latitude, entry.longitude);
                        builder.include(latLng);
                        coordinates.add(latLng);
                    }
                    bounds = builder.build();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                mMap.clear();
                if (coordinates == null || coordinates.isEmpty()) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "No Entries found for that date");
                    }
                } else {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, BOUNDING_BOX_PADDING_PX));
                    mMap.addPolyline(new PolylineOptions().geodesic(true).addAll(coordinates));
                }
            }

        }.execute(calendar);
    }

    private void setMapListener( ) {
        try {
            mMap.setOnMapClickListener( mapClickListener );
            mMap.setOnMarkerClickListener( markerClickListener );
            mMap.setOnMyLocationButtonClickListener( myLocationListener );
        } catch( SecurityException e ) {
            e.printStackTrace();
        }
    }

    private GoogleMap.OnMapClickListener mapClickListener = new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            Log.d(TAG, "mapClick: " + latLng.toString() );
        }
    };

    private GoogleMap.OnMarkerClickListener markerClickListener = new GoogleMap.OnMarkerClickListener() {
        @Override
        public boolean onMarkerClick(Marker marker) {
            Log.d(TAG, "markerClick: " + marker.getTitle());
            return false;
        }
    };

    private GoogleMap.OnMyLocationButtonClickListener myLocationListener = new GoogleMap.OnMyLocationButtonClickListener() {
        @Override
        public boolean onMyLocationButtonClick() {
            Log.d(TAG, "myLocClick:");
            // Return false so that we don't consume the event and the default behavior still occurs
            // (the camera animates to the user's current position).
            return false;
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (mClient.isConnected()) {
            startLocationUpdates( mClient, locationRequest, locationListener);
            com.edicon.activity.common.logger.Log.d(TAG, "Location update resumed");
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
        com.edicon.activity.common.logger.Log.d(TAG, " --> onStart");

        mClient.connect();
        com.edicon.activity.common.logger.Log.d(TAG, " --> mClient.connect");
    }

    @Override
    public void onStop() {
        super.onStop();
        com.edicon.activity.common.logger.Log.d(TAG, " --> onStop");

        mClient.disconnect();
        com.edicon.activity.common.logger.Log.d(TAG, " --> mClient.disconnect");
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

