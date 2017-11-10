package ru.led.carmon;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.quartz.CronExpression;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Observable;


public class CarState extends Observable {
    public static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Location location, lastLocation;
    private JSONArray track;

    private int appVersion = 0;
    private boolean MqttConnected = false;
    private int queueLength = 0;
    private String status = "stopped";

    private boolean gpsEnabled;
    private int satellites;
    private int satellitesUsed;
    private int timeToFirstFix;
    private float moveDistance = 0;

    private boolean fineLocation;

    private int batteryLevel;
    private int batteryTemperature;
    private int batteryVoltage;
    private int batteryPlugged;
    private boolean batteryWarning;
    private boolean batteryNotified;
    private SharedPreferences preferences;

    private CronExpression locateSchedule, wakeSchedule, alarmSchedule;

    public CarState( SharedPreferences preferences ) {
        this.preferences = preferences;
        startNewTrack();

        setLocateSchedule(
                preferences.getString("locate_schedule", "0 0 7,23 * * ?")
        );
        setWakeSchedule(
                preferences.getString("wake_schedule", "0 20 */2 * * ?")
        );
        setAlarmSchedule(
                preferences.getString("detect_schedule", "0 40 */2 * * ?")
        );
    }


    private Calendar getNextTime(CronExpression expr){
        if( expr!=null ){
            Calendar nextDate = Calendar.getInstance();
            nextDate.setTime( expr.getNextValidTimeAfter( new Date() ) );
            return nextDate;
        }
        return null;
    }

    public void setLocateSchedule(String inputString){
        try {
            locateSchedule = new CronExpression(inputString);
            preferences.edit()
                    .putString("locate_schedule", inputString)
                    .apply();
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Error save cron expression", e );
        }
    }
    public String getLocateSchedule(){
        return locateSchedule==null?"":locateSchedule.getCronExpression();
    }

    public Calendar getNextLocateTime(){
        return getNextTime(locateSchedule);
    }
    public Calendar getNextWakeTime(){
        return getNextTime(wakeSchedule);
    }
    public Calendar getNextAlarmTime(){
        return getNextTime(alarmSchedule);
    }

    public String getWakeSchedule() {
        return wakeSchedule==null?"":wakeSchedule.getCronExpression();
    }
    public void setWakeSchedule(String inputString) {
        try {
            wakeSchedule = new CronExpression(inputString);
            preferences.edit()
                    .putString("wake_schedule", inputString)
                    .apply();
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Error save cron expression", e );
        }
    }

    public String getAlarmSchedule() {
        return alarmSchedule ==null?"": alarmSchedule.getCronExpression();
    }

    public void setAlarmSchedule(String inputString) {
        try {
            alarmSchedule = new CronExpression(inputString);
            preferences.edit()
                    .putString("detect_schedule", inputString)
                    .apply();
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Error save cron expression", e );
        }
    }

    public int getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(int appVersion) {
        this.appVersion = appVersion;
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

        message.put("_ver", getAppVersion() );
        message.put("batt", getBatteryLevel() );
        message.put("charge", getBatteryPlugged() );
        message.put("temp", getBatteryTemperature()/10.0 );
        message.put("volt", getBatteryVoltage());
        message.put("tst", (new Date()).getTime()/1000 );

        Location loc = getLocation();
        if( loc!=null ) {
            message.put("_type", "location")
                    .put("acc", loc.getAccuracy())
                    .put("lat", loc.getLatitude())
                    .put("lon", loc.getLongitude())
                    .put("tst", loc.getTime() / 1000)
                    .put("src", loc.getProvider());

            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                message
                        .put("vel", loc.getSpeed())
                        .put("cog", loc.getBearing())
                        .put("alt", loc.getAltitude())
                        .put("ttf", getTimeToFirstFix())
                        .put("sat", String.format("%d/%d", getSatellitesUsed(), getSatellites()));
            }
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
    private void replaceLastPoint(){
        try{
            if( track.length()>0 ){
                track.put( track.length()-1, toJSON() );
            }
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
        if( location==null ){
            return;
        }
        // Set fine location flag
        String provider = location.getProvider();
        float minDistance = provider.equals(LocationManager.NETWORK_PROVIDER) ? 600:50;
        fineLocation = (provider.equals(LocationManager.GPS_PROVIDER) || !gpsEnabled) && location.getAccuracy() < minDistance;

        if( lastLocation!=null && location!=null ){
            setMoveDistance( location.distanceTo(lastLocation) );
        }

        if( isTracking() && fineLocation ){
            if (lastLocation==null || getMoveDistance() >= getTrackDistance()){
                addLocationToTrack();
            }else{
                replaceLastPoint();
            }
            lastLocation = location;
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

    public float getMoveDistance() {
        return moveDistance;
    }

    public void setMoveDistance(float moveDistance) {
        this.moveDistance = moveDistance;
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

    private Float alertDistance = null;
    public Float getAlertDistance() {
        if( alertDistance==null ){
            alertDistance = preferences.getFloat("alert_distance", (float) 500.0);
        }
        return alertDistance;
    }

    public void setAlertDistance(Float alertDistance) {
        this.alertDistance = alertDistance;
        preferences.edit().putFloat("alert_distance", alertDistance).apply();
    }
    public boolean isAlertMoving(){
        return getAlertDistance()!=null && isFineLocation() && getMoveDistance()>=getAlertDistance();
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
