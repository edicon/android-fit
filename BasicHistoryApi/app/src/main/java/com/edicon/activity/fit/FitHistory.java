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

/**
 * This sample demonstrates how to use the History API of the Google Fit platform to insert data,
 * query against existing data, and remove data. It also demonstrates how to authenticate
 * a user with Google Play Services and how to properly represent data in a {@link DataSet}.
 */
public class FitHistory {

    public static final String TAG = "FitHistory";

    /**
     *  Track whether an authorization activity is stacking over the current activity, i.e. when
     *  a known auth error is being resolved, such as showing the account chooser or presenting a
     *  consent dialog. This avoids common duplications as might happen on screen rotations, etc.
     */
    private static AppCompatActivity thisActivity;
    private static GoogleMap thisMap;

    public FitHistory() {}

    public static GoogleApiClient mClient = null;
    public FitHistory(AppCompatActivity activity ) {

        thisActivity = activity;
        // buildFitnessClient();
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
    public void buildFitnessClient( GoogleMap map ) {
        if( mClient != null && mClient.isConnected() ) {
            DataReadRequest readRequest;
            // readRequest = queryFitnessData();
            readRequest = queryLocationData();
            new queryFitDataTask().execute( readRequest );
            return;
        }

        thisMap = map;
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
                                // Now you can make calls to the Fitness APIs.  What to do?
                                boolean query = true;

                                boolean queryLocation = true;
                                DataReadRequest readRequest;
                                if( queryLocation ) {
                                    // readRequest = queryFitnessData();
                                    readRequest = queryLocationData();
                                    new queryFitDataTask().execute( readRequest );
                                } else
                                    new InsertAndVerifyDataTask().execute();
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
                        Log.i(TAG, "Google Play services connection failed. Cause: " + result.toString());
                        Snackbar.make(
                                thisActivity.findViewById(R.id.main_activity_view),
                                "Exception while connecting to Google Play services: " + result.getErrorMessage(),
                                Snackbar.LENGTH_INDEFINITE).show();
                    }
                })
                .build();
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

            // Before querying the data, check to see if the insertion succeeded.
            if (!insertStatus.isSuccess()) {
                Log.i(TAG, "FAIL: Inserting the dataset.");
                return null;
            }

            // At this point, the data has been inserted and can be read.
            Log.i(TAG, "OK: Inserting dataset.");

            // Begin by creating the query.
            DataReadRequest readRequest = queryFitnessData();

            // Invoke the History API to fetch the data with the query and await the result of
            // the read request.
            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);

            // For the sake of the sample, we'll print the data so we can see what we just added.
            // In general, logging fitness information should be avoided for privacy reasons.
            printFitData(dataReadResult);

            readRequest = queryLocationData();
            dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            printFitData(dataReadResult);

            return null;
        }
    }

    /**
     * Create and return a {@link DataSet} of step count data for insertion using the History API.
     */
    private DataSet insertFitnessData() {
        Log.i(TAG, "Insert request.");

        // [START build_insert_data_request]
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

    private static boolean PRINT_FIT_DATA_INFO = false;
    private class queryFitDataTask extends AsyncTask<DataReadRequest, Void, DataReadResult> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if( mClient.isConnected())
                Snackbar.make( thisActivity.findViewById( android.R.id.content), "Start: Query Fit Data", Snackbar.LENGTH_LONG ).show();
            else {
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
            printFitData(dataReadResult);
            Log.d(TAG, "Query Size: " + latLngList.size());

            if( thisMap == null )
                return;

            thisMap.clear();
            LatLngBounds bounds;
            List<LatLng> coordinates = latLngList;
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (LatLng entry : coordinates) {
                LatLng latLng = new LatLng(entry.latitude, entry.longitude);
                builder.include(latLng);
                // coordinates.add(latLng);
            }
            bounds = builder.build();
            if (coordinates == null || coordinates.isEmpty()) {
                if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                    android.util.Log.d(TAG, "No Entries found for that date");
                }
            } else {
                thisMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, TrackingActivity.BOUNDING_BOX_PADDING_PX));
                thisMap.addPolyline(new PolylineOptions().geodesic(true).addAll(coordinates));
            }
        }
    }

    /**
     * Return a {@link DataReadRequest} for all step count changes in the past week.
     */
    public static DataReadRequest queryFitnessData() {
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = getDateInstance();
        Log.i(TAG, "Start: " + dateFormat.format(startTime));
        Log.i(TAG, "End:   " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                        // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                        // bucketByTime allows for a time span, whereas bucketBySession would allow
                        // bucketing by "sessions", which would need to be defined in code.
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        // [END build_read_data_request]

        return readRequest;
    }

    /**
     * Log a record of the query result. It's possible to get more constrained data sets by
     * specifying a data source or data type, but for demonstrative purposes here's how one would
     * dump all the data. In this sample, logging also prints to the device screen, so we can see
     * what the query returns, but your app should not log fitness information as a privacy
     * consideration. A better option would be to dump the data you receive to a local data
     * directory to avoid exposing it to other applications.
     */
    public static void printFitData(DataReadResult dataReadResult) {
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.getBuckets().size() > 0) {
            Log.i(TAG, "Buckets DataSets is: " + dataReadResult.getBuckets().size());
            for (Bucket bucket : dataReadResult.getBuckets()) {
                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    dumpDataSet(dataSet);
                }
            }
        } else if (dataReadResult.getDataSets().size() > 0) {
            Log.i(TAG, "DataSets is: " + dataReadResult.getDataSets().size());
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                dumpDataSet(dataSet);
            }
        }
    }

    private static void dumpDataSet(DataSet dataSet) {
        Log.i(TAG, "Data type: " + dataSet.getDataType().getName());

        DataType dataType = dataSet.getDataType();
        if( dataType.equals(DataType.TYPE_STEP_COUNT_DELTA) ||  dataType.equals(DataType.AGGREGATE_STEP_COUNT_DELTA))
            dumpStepData(dataSet);
        else if( dataType.equals(DataType.TYPE_LOCATION_SAMPLE) ||  dataType.equals( DataType.AGGREGATE_LOCATION_BOUNDING_BOX )) {
            dumpLocationData(dataSet);
        } else
            dumpStepData(dataSet);
    }

    private static void dumpStepData(DataSet dataSet) {
        DateFormat dateFormat = getTimeInstance();

        int i = 0;
        for (DataPoint dp : dataSet.getDataPoints()) {
            Log.i(TAG, "DataPoint:"     + i++);
            Log.i(TAG, "\tDataSource: " + dp.getOriginalDataSource().getAppPackageName());
            Log.i(TAG, "\tType:  "      + dp.getDataType().getName());
            Log.i(TAG, "\tStart: "      + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
            Log.i(TAG, "\tEnd:   "      + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            for(Field field : dp.getDataType().getFields()) {
                Log.i(TAG, "\t" + field.getName() + ": " + dp.getValue(field));
            }
        }
    }


    private static ArrayList<LatLng> latLngList = new ArrayList<LatLng>();
    // http://stackoverflow.com/questions/32373465/get-current-activity-from-google-fit-api-android?rq=1
    private static void dumpLocationData(DataSet dataSet) {
        DateFormat dateFormat = getTimeInstance();

        int i = 0;
        float lat = 0.0f, lng = 0.0f, alt, acc;
        latLngList.clear();
        for (DataPoint dp : dataSet.getDataPoints()) {
            if( PRINT_FIT_DATA_INFO ) {
                Log.i(TAG, "DataPoint: " + i++);
                Log.i(TAG, "\tDataSource: " + dp.getOriginalDataSource().getAppPackageName());
                Log.i(TAG, "\tType:  " + dp.getDataType().getName());
                Log.i(TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                Log.i(TAG, "\tEnd:   " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
            }
            for(Field field : dp.getDataType().getFields()) {
                if( PRINT_FIT_DATA_INFO )
                    Log.i(TAG, "\t" + field.getName() + ": " + (field.getFormat() == Field.FORMAT_FLOAT ? dp.getValue(field).asFloat() : dp.getValue(field)));

                if( field.equals(Field.FIELD_LATITUDE) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    lat = dp.getValue(field).asFloat();
                else if( field.equals(Field.FIELD_LONGITUDE) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    lng = dp.getValue(field).asFloat();
                else if( field.equals(Field.FIELD_ALTITUDE ) && (field.getFormat() == Field.FORMAT_FLOAT ))
                    alt = dp.getValue(field).asFloat();
                else if ( field.equals(Field.FIELD_ACCURACY))
                    acc = dp.getValue(field).asFloat();
            }
            if( dp.getDataType().equals(DataType.TYPE_LOCATION_SAMPLE)) {
                LatLng latLng = new LatLng(lat, lng);
                latLngList.add(latLng);
            }
        }
    }


    public static DataReadRequest queryLocationData() {
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        Calendar cal = Calendar.getInstance();
        Date now = new Date();
        cal.setTime(now);
        long endTime = cal.getTimeInMillis();
        cal.add(Calendar.WEEK_OF_YEAR, -1);
        long startTime = cal.getTimeInMillis();

        DateFormat dateFormat = getDateInstance();
        Log.i(TAG, "Start: " + dateFormat.format(startTime));
        Log.i(TAG, "End:   " + dateFormat.format(endTime));

        DataReadRequest readRequest = new DataReadRequest.Builder()
                // The data request can specify multiple data types to return, effectively
                // combining multiple data queries into one call.
                // In this example, it's very unlikely that the request is for several hundred
                // datapoints each consisting of a few steps and a timestamp.  The more likely
                // scenario is wanting to see how many steps were walked per day, for 7 days.
                // .read(DataType.TYPE_LOCATION_TRACK)
                .read(DataType.TYPE_LOCATION_SAMPLE)            // Detailed Request: Exact timestamp
                // .aggregate(DataType.TYPE_LOCATION_SAMPLE,  DataType.AGGREGATE_LOCATION_BOUNDING_BOX) // Aggregated Request
                // Analogous to a "Group By" in SQL, defines how data should be aggregated.
                // bucketByTime allows for a time span, whereas bucketBySession would allow
                // bucketing by "sessions", which would need to be defined in code.
                // .bucketByTime(1, TimeUnit.HOURS)
                // .bucketByTime(1, TimeUnit.DAYS)
                // .bucketBySession(1, TimeUnit.DAYS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
        // [END build_read_data_request]

        return readRequest;
    }

    /**
     * Delete a {@link DataSet} from the History API. In this example, we delete all
     * step count data for the past 24 hours.
     */
    public void deleteData() {
        Log.i(TAG, "Deleting today's step count data.");

        // [START delete_dataset]
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
    private class GetDailyStepTotalTask extends AsyncTask<Void, Void, Void> {
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
