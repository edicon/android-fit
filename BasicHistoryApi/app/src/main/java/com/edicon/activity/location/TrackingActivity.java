package com.edicon.activity.location;

import com.edicon.activity.ActivityApplication;
import com.edicon.activity.location.db.LocationDataManager;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import android.app.DatePickerDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TextView;

import com.edicon.activity.common.LocationEntry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by mglish.hslee on 2016-10-10.
 */

public class TrackingActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        DatePickerDialog.OnDateSetListener {

    private static final String TAG = "PhoneMainActivity";
    private static final int BOUNDING_BOX_PADDING_PX = 50;
    private TextView mSelectedDateText;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.location_demo);

        mSelectedDateText = (TextView) findViewById(R.id.selected_date);
        // mMap = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
    }

    public void onClick(View view) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        new DatePickerDialog( TrackingActivity.this, TrackingActivity.this,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

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
            String date = DateUtils.formatDateTime(this, calendar.getTimeInMillis(),
                    DateUtils.FORMAT_SHOW_DATE);
            mSelectedDateText.setText(getString(R.string.showing_for_date, date));
            showTrack(calendar);
        }
    }

    /**
     * An {@link android.os.AsyncTask} that is responsible for getting a list of {@link
     * com.edicon.activity.location.common.LocationEntry} objects for a given day and
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
}

