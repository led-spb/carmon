package ru.led.carmon;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Observable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CarState extends Observable {
    public static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Location location, lastLocation;
    private JSONArray track;

    private boolean MqttConnected = false;
    private int queueLength = 0;
    private String status = "stopped";

    private boolean gpsEnabled;
    private int satellites;
    private int satellitesUsed;
    private int timeToFirstFix;

    private boolean fineLocation;

    private int batteryLevel;
    private int batteryTemperature;
    private int batteryVoltage;
    private int batteryPlugged;
    private boolean batteryWarning;
    private boolean batteryNotified;
    private SharedPreferences preferences;

    private ArrayList<Calendar> locateTimes = new ArrayList<Calendar>();

    public CarState( SharedPreferences preferences ) {
        this.preferences = preferences;
        startNewTrack();
        setLocateTimes(
                preferences.getString("locate_times", "07:00 23:00")
        );
    }

    private static Pattern locateTimesPattern = Pattern.compile("[^\\d]*(\\d{2}):(\\d{2})[^\\s]*");
    private static SimpleDateFormat localTimesFormat = new SimpleDateFormat("HH:mm");

    public void setLocateTimes(String inputString){
        Matcher m = locateTimesPattern.matcher(inputString);

        ArrayList<Calendar> tmpList = new ArrayList<Calendar>();

        while( m.find() ){
            Integer hour    = Integer.parseInt( m.group(1) ),
                    minutes = Integer.parseInt( m.group(2) );

            Calendar c = Calendar.getInstance();

            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minutes);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);

            tmpList.add(c);
        }

        if( tmpList.size() != 0 || inputString.equals("") ){
            locateTimes = tmpList;
        }
        preferences.edit()
                .putString("locate_times", locateTimesStr())
                .apply();
    }

    public ArrayList<Calendar> getLocateTimes() {
        return locateTimes;
    }

    public String locateTimesStr(){
        StringBuilder s = new StringBuilder();
        for (Calendar c : getLocateTimes()) {
            s.append( localTimesFormat.format(c.getTime()) ).append(" ");
        }
        return s.toString().trim();
    }

    public Calendar getNextLocateTime(){
        Calendar now = Calendar.getInstance(), minDate = null;
        for (Calendar c: getLocateTimes() ){
            while( c.before(now) ){
                c.add( Calendar.DAY_OF_MONTH, 1);
            }
            if( minDate==null || c.before(minDate) ){
                minDate=c;
            }
        }
        return minDate;
    }

    private Calendar wakeTimestamp;
    public Calendar getNextWakeTime(){
        if( wakeTimestamp == null){
            return null;
        }

        Calendar now = Calendar.getInstance();
        int interval = (int)getWakeInterval();
        while( wakeTimestamp.before(now) ){
            wakeTimestamp.add(Calendar.MILLISECOND, interval);
        }
        return wakeTimestamp;
    }

    public void setFirstWake(long offset){
        wakeTimestamp = Calendar.getInstance();
        wakeTimestamp.setTimeInMillis( offset );

        setChanged();
        notifyObservers();
    }

    public int getSatellites() {
        return satellites;
    }

    public void setSatellites(int satellites) {
        this.satellites = satellites;
    }

    public int getSatellitesUsed() {
        return satellitesUsed;
    }

    public void setSatellitesUsed(int satellitesUsed) {
        this.satellitesUsed = satellitesUsed;
    }

    public int getTimeToFirstFix() {
        return timeToFirstFix;
    }

    public void setTimeToFirstFix(int timeToFirstFix) {
        this.timeToFirstFix = timeToFirstFix;
    }

    public int getBatteryTemperature() {
        return batteryTemperature;
    }

    public void setBatteryTemperature(int batteryTemperature) {
        this.batteryTemperature = batteryTemperature;
    }


    private Long wakeInterval = null;
    public long getWakeInterval() {
        if(wakeInterval==null)
            wakeInterval = preferences.getLong("wake_interval", 60*60*1000 );
        return wakeInterval;
    }
    public void setWakeInterval(long wakeInterval) {
        this.wakeInterval = wakeInterval;
        preferences.edit()
                .putLong("wake_interval", wakeInterval)
                .apply();
    }

    public int getBatteryVoltage() {
        return batteryVoltage;
    }

    public void setBatteryVoltage(int batteryVoltage) {
        this.batteryVoltage = batteryVoltage;
    }

    public void setGpsEnabled(boolean gpsEnabled) {
        this.gpsEnabled = gpsEnabled;
    }

    private Long gpsTimeout = null;
    public long getGpsTimeout() {
        if(gpsTimeout==null)
            gpsTimeout = preferences.getLong("gps_timeout", 5*60*1000);
        return gpsTimeout;
    }
    public void setGpsTimeout(long gpsTimeout) {
        this.gpsTimeout = gpsTimeout;
        preferences.edit()
                .putLong("gps_timeout", gpsTimeout )
                .apply();
    }

    private Long idleTimeout;
    public long getIdleTimeout() {
        if(idleTimeout==null)
            idleTimeout = preferences.getLong("idle_timeout", 5*60*1000);
        return idleTimeout;
    }
    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        preferences.edit()
                .putLong("idle_timeout", idleTimeout )
                .apply();
    }

    private Long poweroffTimeout = null;

    public long getPoweroffTimeout() {
        if( poweroffTimeout==null ){
            poweroffTimeout = preferences.getLong("poweroff_timeout", 2*60*1000);
        }
        return poweroffTimeout;
    }

    public void setPoweroffTimeout(long poweroffTimeout) {
        this.poweroffTimeout = poweroffTimeout;
        preferences.edit().putLong("poweroff_timeout", poweroffTimeout).apply();
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject message = new JSONObject();

        message.put("batt", getBatteryLevel() );
        message.put("charge", getBatteryPlugged() );
        message.put("temp", getBatteryTemperature()/10.0 );
        message.put("volt", getBatteryVoltage());

        Location loc = getLocation();
        message.put("_type", "location")
                .put("acc", loc.getAccuracy())
                .put("lat", loc.getLatitude())
                .put("lon", loc.getLongitude())
                .put("tst", loc.getTime() / 1000)
                .put("src", loc.getProvider());

        if( loc.getProvider().equals(LocationManager.GPS_PROVIDER) ) {
            message
                    .put("vel", loc.getSpeed())
                    .put("cog", loc.getBearing())
                    .put("alt", loc.getAltitude())
                    .put("ttf", getTimeToFirstFix())
                    .put("sat", String.format("%d/%d", getSatellitesUsed(), getSatellites()));
        }
        return message;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isCharging(){
        return getBatteryPlugged()>0;
    }

    public boolean isFineLocation() {
        return fineLocation;
    }

    public void addLocationToTrack() {
        try {
            track.put(toJSON());
        }catch(JSONException e){
            // ignore
        }
    }
    public int getTrackSize(){
        return track.length();
    }
    public JSONArray getCurrentTrack(){
        return track;
    }
    public void startNewTrack(){
        track = new JSONArray();
        lastLocation = null;
    }

    public void setLocation(Location location) {
        this.location = location;

        // Set fine location flag
        String provider = location.getProvider();
        float minDistance = provider.equals(LocationManager.NETWORK_PROVIDER) ? 600:50;
        fineLocation = (provider.equals(LocationManager.GPS_PROVIDER) || !gpsEnabled) && location.getAccuracy() < minDistance;

        if( isTracking() && fineLocation && (lastLocation==null || location.distanceTo( lastLocation ) >= getTrackDistance()) ){
            lastLocation = location;
            addLocationToTrack();
        }

        setChanged();
        notifyObservers();
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public void setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
        if( this.batteryLevel<=15 ) {
            batteryWarning = true;
        }
        if( this.batteryLevel>=20 ) {
            batteryWarning = false;
            batteryNotified = false;
        }
    }

    public boolean isBatteryWarning() {
        return batteryWarning;
    }

    public boolean isBatteryNotified() {
        return batteryNotified;
    }

    public void setBatteryNotified(boolean batteryNotified) {
        this.batteryNotified = batteryNotified;
    }

    public int getBatteryPlugged() {
        return batteryPlugged;
    }

    public void setBatteryPlugged(int batteryPlugged) {
        this.batteryPlugged = batteryPlugged;
    }

    private Float trackDistance = null;
    public float getTrackDistance() {
        if( trackDistance==null){
            trackDistance = preferences.getFloat("track_distance", (float) 500.0);
        }
        return trackDistance;
    }

    public void setTrackDistance(float trackDistance) {
        this.trackDistance = trackDistance;
        preferences.edit().putFloat("track_distance", trackDistance).apply();
    }

    private Boolean tracking = null;
    public boolean isTracking() {
        if( tracking == null ){
            tracking = preferences.getBoolean("tracking", true);
        }
        return tracking;
    }
    public void setTracking(boolean tracking) {
        this.tracking = tracking;
        preferences.edit().putBoolean("tracking", tracking);
    }

    private Boolean timeSync = null;
    public boolean isTimeSync() {
        if( timeSync==null )
            timeSync = this.preferences.getBoolean("time_sync", false);
        return timeSync;
    }

    public void setTimeSync(boolean timeSync) {
        this.timeSync = timeSync;
        this.preferences.edit().putBoolean("time_sync", timeSync).apply();
    }


    public String getMqttUrl() {
        return preferences.getString("mqttUrl","");
    }

    public void setMqttUrl(String mqttUrl) {
        this.preferences.edit().putString("mqttUrl", mqttUrl).apply();
    }

    public String getMqttUsername() {
        return preferences.getString("mqttUsername","");
    }

    public void setMqttUsername(String mqttUsername) {
        this.preferences.edit().putString("mqttUsername", mqttUsername).apply();
    }

    public String getMqttPassword() {
        return preferences.getString("mqttPassword", "");
    }

    public void setMqttPassword(String mqttPassword) {
        this.preferences.edit().putString("mqttPassword", mqttPassword).apply();
    }

    public String getMqttClientId() {
        return preferences.getString("mqttClientId", "");
    }

    public void setMqttClientId(String mqttClientId) {
        this.preferences.edit().putString("mqttClientId", mqttClientId).apply();
    }

    public boolean isMqttConnected() {
        return MqttConnected;
    }

    public void setMqttConnected(boolean mqttConnected) {
        MqttConnected = mqttConnected;
        setChanged();
        notifyObservers();
    }

    public int getQueueLength() {
        return queueLength;
    }

    public void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
        setChanged();
        notifyObservers();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        setChanged();
        notifyObservers();
    }


}
