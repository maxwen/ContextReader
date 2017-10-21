package com.maxwen.contextreader;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ListView mEventList;
    private EventCursorAdapter mAdapter;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy:MM:dd kk:mm:ss");
    private Handler mHandler = new Handler();
    private EventsObserver mEventsObserver;
    private static final String EVENTS = "EVENTS";
    private static final String SHARED_PREFERENCES_NAME = "triggers.xml";
    private static final String KEY_FILTERED_APS = "access_points";
    private static final String KEY_FILTERED_DEVICES = "devices";
    private static final String KEY_FILTERED_FENCES = "gefoences";

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
    public static final String KEY_NETWORK_ACTION = "action";
    public static final String KEY_AP_NAME = "ap";
    public static final String KEY_BT_DEVICE_NAME = "device";
    public static final String KEY_NFC_TAG_ID = "tag";
    public static final String KEY_POWER_CHARGING = "charging";
    public static final String KEY_GEOFENCE_NAME = "name";
    public static final String KEY_GEOFENCE_ACTION_TYPE = "action";
    public static final String KEY_GEOFENCE_ACTION_ENTER = "enter";
    public static final String KEY_GEOFENCE_ACTION_LEAVE = "leave";
    public static final String KEY_GEOFENCE_ACTION_CREATE = "create";
    public static final String KEY_BT_DEVICE_ACTION = "action";
    public static final String KEY_BT_DEVICE_ACTION_CONNECT = "connect";
    public static final String KEY_BT_DEVICE_ACTION_DISCONNECT = "disconnect";

    public static final Uri EVENTS_ALL_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/all");
    public static final Uri EVENTS_GEOFENCE_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/geofence");
    public static final Uri EVENTS_NETWORK_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/network");
    public static final Uri EVENTS_BT_URI
            = Uri.parse("content://com.maxwen.contextlistener/events/bluetooth");
    public static final Uri FILTERS_BT_URI
            = Uri.parse("content://com.maxwen.contextlistener/filter/bluetooth");
    public static final Uri FILTERS_NETWORK_URI
            = Uri.parse("content://com.maxwen.contextlistener/filter/network");
    public static final Uri FILTERS_GEOFENCE_URI
            = Uri.parse("content://com.maxwen.contextlistener/filter/geofence");

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
                            if (isFilteredGeofence(geofenceName)) {
                                if (geofenceAction.equals(KEY_GEOFENCE_ACTION_ENTER)) {
                                    createEventNotification("geofence enter " + geofenceName, 1);
                                } else if (geofenceAction.equals(KEY_GEOFENCE_ACTION_LEAVE)) {
                                    createEventNotification("geofence leave " + geofenceName, 1);
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
                            String action = jData.getString(KEY_NETWORK_ACTION);
                            if (networkType.equals("wifi")) {
                                String apName = jData.getString(KEY_AP_NAME);
                                if (isFilteredNetwork(apName)) {
                                    createEventNotification(apName + " - " + action, 2);
                                }
                            }
                        }
                    }
                } else if (uri.equals(EVENTS_BT_URI)) {
                    Cursor c = getBTEvents();
                    if (c.moveToFirst()) {
                        int type = c.getInt(c.getColumnIndex(KEY_TYPE));
                        if (type == KEY_TYPE_BT) {
                            String data = c.getString(c.getColumnIndex(KEY_DATA));
                            JSONObject jData = new JSONObject(data);
                            String deviceName = jData.getString(KEY_BT_DEVICE_NAME);
                            String action = jData.getString(KEY_BT_DEVICE_ACTION);
                            if (isFilteredBTDevice(deviceName)) {
                                createEventNotification(deviceName + " - " + action, 3);
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
        getContentResolver().registerContentObserver(EVENTS_BT_URI,
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

    private Cursor getBTEvents() {
        String orderBy = KEY_TIMESTAMP + " DESC";
        return getContentResolver().query(EVENTS_BT_URI, EVENTS_PROJECTION,
                null, null, orderBy);
    }

    private Cursor getAvailableBTDevices() {
        return getContentResolver().query(FILTERS_BT_URI, null, null, null, null);
    }

    private Cursor getAvailableNetworks() {
        return getContentResolver().query(FILTERS_NETWORK_URI, null, null, null, null);
    }

    private Cursor getAvailableGeofences() {
        return getContentResolver().query(FILTERS_GEOFENCE_URI, null, null, null, null);
    }

    private void createEventNotification(String event, int id) {
        Notification.Builder builder = new Notification.Builder(this, EVENTS)
                .setContentTitle(event);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
        nm.notify(id, builder.build());
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_bt_devices) {
            showBTFilterDialog();
            return true;
        }

        if (id == R.id.action_wifi_networks) {
            showWifiFilterDialog();
            return true;
        }

        if (id == R.id.action_geofences) {
            showGeofenceFilterDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showBTFilterDialog() {
        final Set<String> filteredDevices = getFilteredBTDevices();
        final List<String> deviceList = new ArrayList<String>();
        Cursor cursor = getAvailableBTDevices();
        boolean[] checkedItems = new boolean[cursor.getCount()];
        int i = 0;
        if (cursor.moveToFirst()) {
            do {
                String deviceName = cursor.getString(0);
                deviceList.add(deviceName);
                checkedItems[i] = filteredDevices.contains(deviceName);
                i++;

            } while (cursor.moveToNext());
        }
        cursor.close();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Bluetooth Devices")
                .setMultiChoiceItems(deviceList.toArray(new String[]{}), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            filteredDevices.add(deviceList.get(indexSelected));
                        } else {
                            filteredDevices.remove(deviceList.get(indexSelected));
                        }
                    }
                }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        setFilteredBTDevices(filteredDevices);
                    }
                }).create();
        dialog.show();
    }

    private void showWifiFilterDialog() {
        final List<String> networkList = new ArrayList<String>();
        final Set<String> filteredNetworks = getFilteredNetworks();

        Cursor cursor = getAvailableNetworks();
        boolean[] checkedItems = new boolean[cursor.getCount()];
        int i = 0;
        if (cursor.moveToFirst()) {
            do {
                String network = cursor.getString(0);
                networkList.add(network);
                checkedItems[i] = filteredNetworks.contains(network);
                i++;

            } while (cursor.moveToNext());
        }
        cursor.close();


        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Wireless Networks")
                .setMultiChoiceItems(networkList.toArray(new String[]{}), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            filteredNetworks.add(networkList.get(indexSelected));
                        } else {
                            filteredNetworks.remove(networkList.get(indexSelected));
                        }
                    }
                }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        setFilteredNetworks(filteredNetworks);
                    }
                }).create();
        dialog.show();
    }

    private void showGeofenceFilterDialog() {
        final Set<String> filteredFences = getFilteredGeofences();
        final List<String> fenceList = new ArrayList<>();

        Cursor cursor = getAvailableGeofences();
        boolean[] checkedItems = new boolean[cursor.getCount()];
        int i = 0;
        if (cursor.moveToFirst()) {
            do {
                String fence = cursor.getString(0);
                fenceList.add(fence);
                checkedItems[i] = filteredFences.contains(fence);
                i++;

            } while (cursor.moveToNext());
        }
        cursor.close();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Geofences")
                .setMultiChoiceItems(fenceList.toArray(new String[]{}), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected, boolean isChecked) {
                        if (isChecked) {
                            filteredFences.add(fenceList.get(indexSelected));
                        } else {
                            filteredFences.remove(fenceList.get(indexSelected));
                        }
                    }
                }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        setFilteredGeofences(filteredFences);
                    }
                }).create();
        dialog.show();
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> getFilteredNetworks() {
        return getPrefs().getStringSet(KEY_FILTERED_APS, new HashSet<String>());
    }

    private boolean isFilteredNetwork(String network) {
        return getPrefs().getStringSet(KEY_FILTERED_APS, new HashSet<String>()).contains(network);
    }

    public void setFilteredNetworks(Set<String> networks) {
        getPrefs().edit().putStringSet(KEY_FILTERED_APS, networks).commit();
    }

    private boolean isFilteredBTDevice(String deviceName) {
        return getPrefs().getStringSet(KEY_FILTERED_DEVICES, new HashSet<String>()).contains(deviceName);
    }

    public Set<String> getFilteredBTDevices() {
        return getPrefs().getStringSet(KEY_FILTERED_DEVICES, new HashSet<String>());
    }

    public void setFilteredBTDevices(Set<String> devices) {
        getPrefs().edit().putStringSet(KEY_FILTERED_DEVICES, devices).commit();
    }

    private boolean isFilteredGeofence(String fence) {
        return getPrefs().getStringSet(KEY_FILTERED_FENCES, new HashSet<String>()).contains(fence);
    }

    public Set<String> getFilteredGeofences() {
        return getPrefs().getStringSet(KEY_FILTERED_FENCES, new HashSet<String>());
    }

    public void setFilteredGeofences(Set<String> fences) {
        getPrefs().edit().putStringSet(KEY_FILTERED_FENCES, fences).commit();
    }
}
