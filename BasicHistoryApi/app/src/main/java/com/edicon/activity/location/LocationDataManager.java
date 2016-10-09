package com.edicon.activity.location;

import com.edicon.activity.location.db.LocationDbHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class that wraps database access and provides a cache for various GPS data.
 */
public class LocationDataManager {

    private final Map<String, List<LocationEntry>> mPointsMap = new HashMap<String, List<LocationEntry>>();

    private LocationDbHelper mDbHelper;

    public LocationDataManager(LocationDbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /**
     * Returns a list of {@link com.edicon.activity.location.LocationEntry}
     * objects for the day that the {@link java.util.Calendar} object points at. Internally it uses
     * a cache to speed up subsequent calls. If there is no cached value, it gets the result from
     * the database.
     */
    public final List<LocationEntry> getPoints(Calendar calendar) {
        String day = Utils.getHashedDay(calendar);
        synchronized (mPointsMap) {
            if (mPointsMap.get(day) == null) {
                // there is no cache for this day, so lets get it from DB
                List<LocationEntry> points = mDbHelper.read(calendar);
                mPointsMap.put(day, points);
            }
        }
        return mPointsMap.get(day);
    }

    /**
     * Clears the data for the day that the {@link java.util.Calendar} object falls on. This method
     * removes the entries from the database and updates the cache accordingly.
     */
    public final int clearPoints(Calendar calendar) {
        synchronized (mPointsMap) {
            String day = Utils.getHashedDay(calendar);
            mPointsMap.remove(day);
            return mDbHelper.delete(day);
        }
    }

    /**
     * Adds a {@link com.edicon.activity.location.LocationEntry} point to the
     * database and cache if it is a new point.
     */
    public final void addPoint(LocationEntry entry) {
        synchronized (mPointsMap) {
            List<LocationEntry> points = getPoints(entry.calendar);
            if (points == null || points.isEmpty()) {
                mDbHelper.insert(entry);
                if (points == null) {
                    points = new ArrayList<LocationEntry>();
                }
                points.add(entry);
                mPointsMap.put(entry.day, points);
            } else {
                if (!points.contains(entry)) {
                    mDbHelper.insert(entry);
                    points.add(entry);
                }
            }
        }
    }
}

