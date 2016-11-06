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
package com.edicon.activity.fit;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import com.edicon.activity.TrackingActivity;
import com.edicon.activity.common.logger.Log;
import com.edicon.activity.location.LocationEntry;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fit.samples.basichistoryapi.R;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DailyTotalResult;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.edicon.activity.common.Utils.handleConnectionFailed;
import static java.text.DateFormat.getDateInstance;
import static java.text.DateFormat.getTimeInstance;

public class FitHistory {

    public static final String TAG = "FitHistory";

    private static final int SKIP_TIME_INTERVAL_SECOND = 5; // *1000;
    private AppCompatActivity thisActivity;
    private static GoogleMap thisMap;
    private static GoogleApiClient mClient;

    public FitHistory() {}

    public FitHistory(AppCompatActivity activity, GoogleMap map ) {

        thisActivity = activity;
        thisMap = map;

        buildFitnessClient();
        // buildSensorListener();
    }

    /**
     *  Build a {@link GoogleApiClient} that will authenticate the user and allow the application
     *  to connect to Fitness APIs. The scopes included should match the scopes your app needs
     *  (see documentation for details). Authentication will occasionally fail intentionally,
     *  and in those cases, there will be a known resolution, which the OnConnectionFailedListener()
     *  can address. Examples of this include the user never having signed in before, or
     *  having multiple accounts on the device and needing to specify which account to use, etc.
     */
    public void buildFitnessClient() {
        mClient = new GoogleApiClient.Builder(thisActivity)
                .addApi(Fitness.HISTORY_API)
                .useDefaultAccount()                    // setAccountName( String )
                // https://github.com/cyfung/ActivityRecognitionSample/blob/master/app/src/main/java/com/aucy/activityrecognitionsample/MainActivity.java
                // .addApi(ActivityRecognition.API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ_WRITE))
                .addScope(new Scope(Scopes.FITNESS_LOCATION_READ_WRITE))
                .addConnectionCallbacks(
                        new ConnectionCallbacks() {
                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(TAG, "Google Service: Connected!!!");
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        handleConnectionFailed( thisActivity, mClient, connectionResult );
                    }
                })
                .enableAutoManage(thisActivity, 0, new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.i(TAG, "Google Services: Failed: " + result.toString());
                        Snackbar.make(
                                thisActivity.findViewById(R.id.main_activity_view),
                                "Google services: Failed: " + result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
    }

    public void startTrackingDataTask( Calendar cal ) {
        if( mClient != null && mClient.isConnected() ) {
            DataReadRequest readRequest;
            readRequest = queryLocationData( cal );
            new queryFitDataTask().execute( readRequest );
            return;
        }
        Snackbar.make( thisActivity.findViewById(android.R.id.content), "Google Fit: NOT Connected", Snackbar.LENGTH_SHORT).show();
    }

    public void startFitDataTask( Calendar cal ) {
        if( mClient != null && mClient.isConnected() ) {
            DataReadRequest readRequest;
            readRequest = queryFitnessData( cal );
            new queryFitDataTask().execute( readRequest );
            return;
        }
        Snackbar.make( thisActivity.findViewById(android.R.id.content), "Google Fit: NOT Connected", Snackbar.LENGTH_SHORT).show();
    }

    private static boolean PRINT_FIT_DATA_INFO = false;
    private class queryFitDataTask extends AsyncTask<DataReadRequest, Void, DataReadResult> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if( !mClient.isConnected()) {
                Snackbar.make( thisActivity.findViewById( android.R.id.content), "Google Service: NOT Connected", Snackbar.LENGTH_LONG ).show();
                return;
            }
        }

        protected DataReadResult doInBackground(DataReadRequest... params) {
            Log.i(TAG, "START: queryFitData");

            DataReadRequest readRequest = params[0];
            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

            return dataReadResult;
        }

        @Override
        protected void onPostExecute( DataReadResult dataReadResult) {
            super.onPostExecute(dataReadResult);

            PRINT_FIT_DATA_INFO = false;
            parseFitData(dataReadResult);
            Log.d(TAG, "Query Size: " + latLngList.size());

            if( thisMap == null )
                return;
            if( latLngList.size() < 1 ) {
                Snackbar.make( thisActivity.findViewById( android.R.id.content), "Google Fit: No Tracking Data", Snackbar.LENGTH_LONG ).show();
                return;
            }
            thisMap.clear();
            dispPolyline( thisMap, latLngList );
        }
    }

    private void dispPolyline( GoogleMap thisMap, List<LatLng> latLngs ) {
        LatLngBounds bounds;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng entry : latLngs) {
            LatLng latLng = new LatLng(entry.latitude, entry.longitude);
            builder.include(latLng);
        }
        bounds = builder.build();
        PolylineOptions polylineOptions = new PolylineOptions()
                .width(5)
                .color(Color.RED)
                .geodesic(true)
                .clickable(true)
                .addAll(latLngs);
        thisMap.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
            @Override
            public void onPolylineClick(Polyline polyline) {
                // Flip the values of the r, g and b components of the polyline's color.
                int strokeColor = polyline.getColor() ^ 0x00ffffff;
                polyline.setColor(strokeColor);
            }
        });
        thisMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, TrackingActivity.BOUNDING_BOX_PADDING_PX));
        thisMap.addPolyline(polylineOptions);
    }

    public static DataReadRequest queryLocationData( Calendar cal ) {
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        // cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = getDateInstance();
        Log.i(TAG, "Start: " + dateFormat.format(startTime));
        Log.i(TAG, "End:   " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred datapoints each consisting of a few steps and a timestamp.
                // The more likely scenario is wanting to see how many steps were walked per day, for 7 days.
                // .read(DataType.TYPE_LOCATION_TRACK)
                .read(DataType.TYPE_LOCATION_SAMPLE)            // Detailed Request: Exact timestamp
                // .aggregate(DataType.TYPE_LOCATION_SAMPLE,  DataType.AGGREGATE_LOCATION_BOUNDING_BOX) // Aggregated Request
                // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                // bucketByTime allows for a time span, whereas bucketBySession would allow
                // bucketing by "sessions", which would need to be defined in code.
                // .bucketByTime(1, TimeUnit.HOURS) // .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        return readRequest;
    }

    public static DataReadRequest queryFitnessData( Calendar cal ) {
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);         // a day per week
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = getDateInstance();
        Log.i(TAG, "Start: " + dateFormat.format(startTime));
        Log.i(TAG, "End:   " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                //  ToDo: Add multi-type and filtering results
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        return readRequest;
    }

    /**
     *  Create a {@link DataSet} to insert data into the History API, and
     *  then create and execute a {@link DataReadRequest} to verify the insertion succeeded.
     *  By using an {@link AsyncTask}, we can schedule synchronous calls, so that we can query for
     *  data after confirming that our insert was successful. Using asynchronous calls and callbacks
     *  would not guarantee that the insertion had concluded before the read request was made.
     *  An example of an asynchronous call using a callback can be found in the example
     *  on deleting data below.
     */
    private class InsertAndVerifyDataTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {
            // Create a new dataset and insertion request.
            DataSet dataSet = insertFitnessData();

            // Then, invoke the History API to insert the data and await the result, which is
            // possible here because of the {@link AsyncTask}. Always include a timeout when calling
            // await() to prevent hanging that can occur from the service being shutdown because
            // of low memory or other conditions.
            Log.i(TAG, "History: Inserting the dataset.");
            com.google.android.gms.common.api.Status insertStatus = Fitness.HistoryApi.insertData(mClient, dataSet).await(1, TimeUnit.MINUTES);

            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "FAIL: Inserting the dataset.");
                return null;
            }
            Log.i(TAG, "OK: Inserting dataset.");

            DataReadRequest readRequest = queryFitnessData(getTodayCalendar());
            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            parseFitData(dataReadResult);

            readRequest = queryLocationData( FitHistory.getTodayCalendar());
            dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            parseFitData(dataReadResult);

            return null;
        }
    }

    /**
     * Create and return a {@link DataSet} of step count data for insertion using the History API.
     */
    private DataSet insertFitnessData() {
        Log.i(TAG, "Insert request.");

        // Set a start and end time for our data, using a start time of 1 hour before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.HOUR_OF_DAY, -1);
        long startTime = cal.getTimeInMillis();

        // Create a data source
        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(thisActivity)
                .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .setStreamName(TAG + " - step count")
                .setType(DataSource.TYPE_RAW)
                .build();

        // Create a data set
        int stepCountDelta = 950;
        DataSet dataSet = DataSet.create(dataSource);
        // For each data point, specify a start time, end time, and the data value -- in this case,
        // the number of new steps.
        DataPoint dataPoint = dataSet.createDataPoint().setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS);
        dataPoint.getValue(Field.FIELD_STEPS).setInt(stepCountDelta);
        dataSet.add(dataPoint);
        // [END build_insert_data_request]

        return dataSet;
    }

    /**
     * Log a record of the query result. It's possible to get more constrained data sets by
     * specifying a data source or data type, but for demonstrative purposes here's how one would
     * dump all the data. In this sample, logging also prints to the device screen, so we can see
     * what the query returns, but your app should not log fitness information as a privacy
     * consideration. A better option would be to dump the data you receive to a local data
     * directory to avoid exposing it to other applications.
     */
    public static void parseFitData(DataReadResult dataReadResult) {
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Buckets DataSets is: " + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    parseDataSet(dataSet);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "DataSets is: " + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                parseDataSet(dataSet);
            }
        }
    }

    private static void parseDataSet(DataSet dataSet) {

        latLngList.clear();

        if( dataSet.getDataType().equals(DataType.TYPE_LOCATION_SAMPLE ))
            parseLocationData( dataSet );
        else if( dataSet.getDataType().equals(DataType.TYPE_STEP_COUNT_DELTA ))
            parseStepCount( dataSet );
        else
            parseStepCount( dataSet );
    }

    // http://stackoverflow.com/questions/32373465/get-current-activity-from-google-fit-api-android?rq=1
    private static ArrayList<LatLng> latLngList = new ArrayList<LatLng>();
    private static void parseLocationData( DataSet dataSet) {
        int i = 0;
        float lat = 0.0f, lng = 0.0f, alt, acc;

        long prevTimeStamp = 0, currTimeStamp;
        for (DataPoint dp : dataSet.getDataPoints()) {
            dumpDataPoint( i++, dp );       // Dump DataPoint
            // ToDo: Skip when query or Check Time Interval
            currTimeStamp = dp.getTimestamp(TimeUnit.SECONDS);
            if( currTimeStamp < (prevTimeStamp + SKIP_TIME_INTERVAL_SECOND))
                continue;
            prevTimeStamp = currTimeStamp;

            for(Field field : dp.getDataType().getFields()) {
                dumpField( dp, field );     // Dump Field
                if( field.equals(Field.FIELD_LATITUDE) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    lat = dp.getValue(field).asFloat();
                else if( field.equals(Field.FIELD_LONGITUDE) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    lng = dp.getValue(field).asFloat();
                else if( field.equals(Field.FIELD_ALTITUDE ) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    alt = dp.getValue(field).asFloat();
                else if ( field.equals(Field.FIELD_ACCURACY))
                    acc = dp.getValue(field).asFloat();
            }
            LatLng latLng = new LatLng(lat, lng);
            latLngList.add(latLng);
        }
    }

    private static int stepDelta;
    private static void parseStepCount( DataSet dataSet) {
        int i = 0;
        String fitData;

        for (DataPoint dp : dataSet.getDataPoints()) {
            dumpDataPoint(i++, dp);     // Dump DataPoint
            for (Field field : dp.getDataType().getFields()) {
                dumpField(dp, field); // Dump Field
                if (field.equals(Field.FIELD_STEPS) && (field.getFormat() == Field.FORMAT_INT32))
                    stepDelta = dp.getValue(field).asInt();
                else if (field.getFormat() == Field.FORMAT_STRING)
                    fitData = dp.getValue(field).asString();
            }
        }
    }

    private static void dumpDataPoint( int i, DataPoint dp ) {
        DateFormat dateFormat = getTimeInstance();
        if( PRINT_FIT_DATA_INFO ) {
            Log.i(TAG, "DataPoint: " + i);
            Log.i(TAG, "\tDataSource: " + dp.getOriginalDataSource().getAppPackageName());
            Log.i(TAG, "\tType:  "      + dp.getDataType().getName());
            Log.i(TAG, "\tStart: "      + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd:   "      + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tTimeStamp: "  + dateFormat.format(dp.getTimestamp(TimeUnit.MILLISECONDS)));
        }
    }

    private static void dumpField( DataPoint dp, Field field ) {
        if( PRINT_FIT_DATA_INFO ) {
            Log.i(TAG, "\t" + field.getName() + ": " + (field.getFormat() == Field.FORMAT_FLOAT ? dp.getValue(field).asFloat() : dp.getValue(field)));
        }
    }

    public static Calendar getTodayCalendar() {
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);

        return cal;
    }

    /**
     * Delete a {@link DataSet} from the History API. In this example, we delete all
     * step count data for the past 24 hours.
     */
    public void deleteData() {
        Log.i(TAG, "Deleting today's step count data.");

        // Set a start and end time for our data, using a start time of 1 day before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        //  Create a delete request object, providing a data type and a time interval
        DataDeleteRequest request = new DataDeleteRequest.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                .build();

        // Invoke the History API with the Google API client object and delete request, and then
        // specify a callback that will check the result.
        Fitness.HistoryApi.deleteData(mClient, request)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Successfully deleted today's step count data.");
                        } else {
                            // The deletion will fail if the requesting app tries to delete data
                            // that it did not insert.
                            Log.i(TAG, "Failed to delete today's step count data.");
                        }
                    }
                });
        // [END delete_dataset]
    }

    // Read the Daily Step Total: https://developers.google.com/fit/scenarios/read-daily-step-total
    private class getDailyStepTotalTask extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... params) {

            long total = 0;

            PendingResult<DailyTotalResult> result = Fitness.HistoryApi.readDailyTotal(mClient, DataType.TYPE_STEP_COUNT_DELTA);
            DailyTotalResult totalResult = result.await(30, TimeUnit.SECONDS);
            if (totalResult.getStatus().isSuccess()) {
                DataSet totalSet = totalResult.getTotal();
                total = totalSet.isEmpty()
                        ? 0
                        : totalSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
            } else {
                Log.w(TAG, "There was a problem getting the step count.");
            }
            Log.i(TAG, "Total steps: " + total);

            return null;
        }
    }
}
