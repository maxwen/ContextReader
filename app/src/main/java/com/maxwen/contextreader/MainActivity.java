package com.maxwen.contextreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ListView mEventList;
    private EventCursorAdapter mAdapter;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss");
    private Handler mHandler = new Handler();
    private EventsObserver mEventsObserver;
    private static final String EVENTS = "EVENTS";

    public static final String KEY_ID = "_id";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_DATA = "data";
    public static final String KEY_TYPE = "type";

    public static final int KEY_TYPE_UNKNOWN = 0;
    public static final int KEY_TYPE_LOCATION = 1;
    public static final int KEY_TYPE_NETWORK = 2;
    public static final int KEY_TYPE_BT = 3;
    public static final int KEY_TYPE_NFC = 4;
    public static final int KEY_TYPE_POWER = 5;
    public static final int KEY_TYPE_GEFOENCE = 6;

    public static final String KEY_LOCATION_LAT = "lat";
    public static final String KEY_LOCATION_LONG = "long";
    public static final String KEY_NETWORK_TYPE = "network";
    public static final String KEY_AP_NAME = "ap";
    public static final String KEY_BT_DEVICE_NAME = "device";
    public static final String KEY_NFC_TAG_ID = "tag";
    public static final String KEY_POWER_CHARGING = "charging";
    public static final String KEY_GEOFENCE_NAME = "name";
    public static final String KEY_GEOFENCE_ACTION_TYPE = "action";
    public static final String KEY_GEOFENCE_ACTION_ENTER = "enter";
    public static final String KEY_GEOFENCE_ACTION_LEAVE = "leave";
    public static final String KEY_GEOFENCE_ACTION_CREATE = "create";

    public static final Uri EVENTS_ALL_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/all");
    public static final Uri EVENTS_GEOFENCE_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/geofence");
    public static final Uri EVENTS_NETWORK_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/network");

    final String[] EVENTS_PROJECTION = new String[]{
            KEY_ID,
            KEY_TIMESTAMP,
            KEY_TYPE,
            KEY_DATA
    };

    private static final String HOME_WIFI = "maxwen";
    private static final String HOME_GEOFENCE = "home";

    private class EventCursorAdapter extends CursorAdapter {
        public EventCursorAdapter(Context context, Cursor cursor) {
            super(context, cursor, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.event_item, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView timeStampText = view.findViewById(R.id.timestamp);
            TextView dataText = view.findViewById(R.id.data);
            long timeStamp = cursor.getLong(cursor.getColumnIndex(KEY_TIMESTAMP));
            String data = cursor.getString(cursor.getColumnIndex(KEY_DATA));
            TIME_FORMAT.setTimeZone(TimeZone.getDefault());
            timeStampText.setText(TIME_FORMAT.format(timeStamp));
            dataText.setText(data);
        }
    }

    private class EventsObserver extends ContentObserver {
        EventsObserver() {
            super(mHandler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            Log.d(TAG, "onChange " + uri);

            try {
                if (uri.equals(EVENTS_ALL_URI)) {
                    mAdapter.swapCursor(getEvents());
                } else if (uri.equals(EVENTS_GEOFENCE_URI)) {
                    Cursor c = getGeofenceEvents();
                    if (c.moveToFirst()) {
                        int type = c.getInt(c.getColumnIndex(KEY_TYPE));
                        if (type == KEY_TYPE_GEFOENCE) {
                            String data = c.getString(c.getColumnIndex(KEY_DATA));
                            JSONObject jData = new JSONObject(data);
                            String geofenceName = jData.getString(KEY_GEOFENCE_NAME);
                            String geofenceAction = jData.getString(KEY_GEOFENCE_ACTION_TYPE);
                            if (geofenceName.equals(HOME_GEOFENCE)) {
                                if (geofenceAction.equals(KEY_GEOFENCE_ACTION_ENTER)) {
                                    createEventNotification("geofence enter " + geofenceName);
                                } else if (geofenceAction.equals(KEY_GEOFENCE_ACTION_LEAVE)) {
                                    createEventNotification("geofence leave" + geofenceName);
                                }
                            }
                        }
                    }
                } else if (uri.equals(EVENTS_NETWORK_URI)) {
                    Cursor c = getNetworkEvents();
                    if (c.moveToFirst()) {
                        int type = c.getInt(c.getColumnIndex(KEY_TYPE));
                        if (type == KEY_TYPE_NETWORK) {
                            String data = c.getString(c.getColumnIndex(KEY_DATA));
                            JSONObject jData = new JSONObject(data);
                            String networkType = jData.getString(KEY_NETWORK_TYPE);
                            if (networkType.equals("wifi")) {
                                String apName = jData.getString(KEY_AP_NAME);
                                if (apName.indexOf(HOME_WIFI) != -1) {
                                    createEventNotification("network event " + apName);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "onChange " + uri, e);

            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        NotificationChannel channel = new NotificationChannel(
                EVENTS,
                "Events",
                NotificationManager.IMPORTANCE_HIGH);

        List<NotificationChannel> channelList = new ArrayList<>();
        channelList.add(channel);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannels(channelList);

        mEventsObserver = new EventsObserver();

        mAdapter = new EventCursorAdapter(this, getEvents());
        mEventList = (ListView) findViewById(R.id.event_list);
        mEventList.setAdapter(mAdapter);

        getContentResolver().registerContentObserver(EVENTS_ALL_URI,
                false, mEventsObserver);
        getContentResolver().registerContentObserver(EVENTS_GEOFENCE_URI,
                false, mEventsObserver);
        getContentResolver().registerContentObserver(EVENTS_NETWORK_URI,
                false, mEventsObserver);
        mAdapter.swapCursor(getEvents());

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mEventsObserver);
    }

    private Cursor getEvents() {
        String orderBy = KEY_TIMESTAMP + " DESC";
        return getContentResolver().query(EVENTS_ALL_URI, EVENTS_PROJECTION,
                null, null, orderBy);
    }

    private Cursor getNetworkEvents() {
        String orderBy = KEY_TIMESTAMP + " DESC";
        return getContentResolver().query(EVENTS_NETWORK_URI, EVENTS_PROJECTION,
                null, null, orderBy);
    }

    private Cursor getGeofenceEvents() {
        String orderBy = KEY_TIMESTAMP + " DESC";
        return getContentResolver().query(EVENTS_GEOFENCE_URI, EVENTS_PROJECTION,
                null, null, orderBy);
    }

    private void createEventNotification(String event) {
        Notification.Builder builder = new Notification.Builder(this, EVENTS)
                .setContentTitle(event);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(1);
        nm.notify(1, builder.build());
    }
}
