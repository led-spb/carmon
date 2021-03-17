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
    static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Location location, lastLocation;
    private JSONArray track;

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
    private int versionCode;

    private CronExpression locateSchedule, wakeSchedule, alarmSchedule;

    CarState( SharedPreferences preferences ) {
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


    SharedPreferences getPreferences(){
        return preferences;
    }

    private Calendar getNextTime(CronExpression expr){
        if( expr!=null ){
            Calendar nextDate = Calendar.getInstance();
            nextDate.setTime( expr.getNextValidTimeAfter( new Date() ) );
            return nextDate;
        }
        return null;
    }

    int getVersionCode() {
        return versionCode;
    }

    void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
    }

    void setLocateSchedule(String inputString){
        locateSchedule = null;
        try {
            locateSchedule = new CronExpression(inputString);
        } catch (ParseException e) {
            Log.e(getClass().getPackage().getName(), "Error save cron expression", e);
        }
        preferences.edit()
                .putString("locate_schedule", inputString)
                .apply();
    }
    String getLocateSchedule(){
        return locateSchedule==null?"":locateSchedule.getCronExpression();
    }

    Calendar getNextLocateTime(){
        return getNextTime(locateSchedule);
    }
    Calendar getNextWakeTime(){
        return getNextTime(wakeSchedule);
    }
    Calendar getNextAlarmTime(){
        return getNextTime(alarmSchedule);
    }

    String getWakeSchedule() {
        return wakeSchedule==null?"":wakeSchedule.getCronExpression();
    }
    void setWakeSchedule(String inputString) {
        wakeSchedule = null;
        try {
            wakeSchedule = new CronExpression(inputString);
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Error save cron expression", e );
        }
        preferences.edit()
                .putString("wake_schedule", inputString)
                .apply();
    }

    String getAlarmSchedule() {
        return alarmSchedule == null?"": alarmSchedule.getCronExpression();
    }

    void setAlarmSchedule(String inputString) {
        alarmSchedule = null;
        try {
            alarmSchedule = new CronExpression(inputString);
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Error save cron expression", e );
        }
        preferences.edit()
                .putString("detect_schedule", inputString)
                .apply();
    }

    int getSatellites() {
        return satellites;
    }

    void setSatellites(int satellites) {
        this.satellites = satellites;
    }

    int getSatellitesUsed() {
        return satellitesUsed;
    }

    void setSatellitesUsed(int satellitesUsed) {
        this.satellitesUsed = satellitesUsed;
    }

    int getTimeToFirstFix() {
        return timeToFirstFix;
    }

    void setTimeToFirstFix(int timeToFirstFix) {
        this.timeToFirstFix = timeToFirstFix;
    }

    int getBatteryTemperature() {
        return batteryTemperature;
    }

    void setBatteryTemperature(int batteryTemperature) {
        this.batteryTemperature = batteryTemperature;
    }

    int getBatteryVoltage() {
        return batteryVoltage;
    }

    void setBatteryVoltage(int batteryVoltage) {
        this.batteryVoltage = batteryVoltage;
    }

    void setGpsEnabled(boolean gpsEnabled) {
        this.gpsEnabled = gpsEnabled;
    }

    private Long locationTimeout = null;
    long getLocationTimeout() {
        if(locationTimeout ==null)
            locationTimeout = preferences.getLong("location_timeout", 5*60*1000);
        return locationTimeout;
    }
    void setLocationTimeout(long locationTimeout) {
        this.locationTimeout = locationTimeout;
        preferences.edit()
                .putLong("location_timeout", locationTimeout)
                .apply();
    }

    private Long idleTimeout;
    long getIdleTimeout() {
        if(idleTimeout==null)
            idleTimeout = preferences.getLong("idle_timeout", 5*60*1000);
        return idleTimeout;
    }
    void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        preferences.edit()
                .putLong("idle_timeout", idleTimeout )
                .apply();
    }

    private Long poweroffTimeout = null;
    long getPoweroffTimeout() {
        if( poweroffTimeout==null ){
            poweroffTimeout = preferences.getLong("poweroff_timeout", 2*60*1000);
        }
        return poweroffTimeout;
    }

    public void setPoweroffTimeout(long poweroffTimeout) {
        this.poweroffTimeout = poweroffTimeout;
        preferences.edit().putLong("poweroff_timeout", poweroffTimeout).apply();
    }

    JSONObject toJSON() throws JSONException{
        return toJSON(false);
    }

    JSONObject toJSON(boolean less) throws JSONException {
        JSONObject message = new JSONObject();

        if(!less) {
            message.put("_type", "location");
            message.put("_ver", getVersionCode() );
            message.put("batt", getBatteryLevel());
            message.put("bs", isCharging()? 2: 1);
            message.put("temp", getBatteryTemperature() / 10.0);
            message.put("volt", getBatteryVoltage());
        }

        Location loc = getLocation();
        if( loc!=null ) {
            message
                    .put("acc", loc.getAccuracy())
                    .put("lat", loc.getLatitude())
                    .put("lon", loc.getLongitude())
                    .put("tst", loc.getTime() / 1000)
                    .put("src", loc.getProvider());

            if (loc.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                message
                        .put("vel", loc.getSpeed())
                        .put("alt", loc.getAltitude());

                if(!less) {
                    message
                            //.put("cog", loc.getBearing()) // Not used in current android version
                            .put("ttf", getTimeToFirstFix())
                            .put("sat", String.format("%d/%d", getSatellitesUsed(), getSatellites()));
                }
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
            track.put(toJSON(true));
        }catch(JSONException e){
            // ignore
        }
    }
    private void replaceLastPoint(){
        try{
            if( track.length()>0 ){
                track.put( track.length()-1, toJSON(true) );
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
            if (lastLocation==null
                    || getMoveDistance() >= getTrackDistance()
                    /*|| Math.abs(location.getTime()-lastLocation.getTime())> getTrackTime()*/ ){
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

    float getMoveDistance() {
        return moveDistance;
    }

    void setMoveDistance(float moveDistance) {
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

    int getBatteryPlugged() {
        return batteryPlugged;
    }
    void setBatteryPlugged(int batteryPlugged) {
        this.batteryPlugged = batteryPlugged;
    }

    private Float trackDistance = null;
    float getTrackDistance() {
        if( trackDistance==null){
            trackDistance = preferences.getFloat("track_distance", (float) 200.0);
        }
        return trackDistance;
    }
    void setTrackDistance(float trackDistance) {
        this.trackDistance = trackDistance;
        preferences.edit().putFloat("track_distance", trackDistance).apply();
    }

    private Float alarmDistance = null;
    public float getAlarmDistance() {
        if( alarmDistance == null ){
            alarmDistance = preferences.getFloat("alert_distance", (float) 500.0);
        }
        return alarmDistance;
    }

    public void setAlarmDistance(Float alarmDistance) {
        this.alarmDistance = alarmDistance;
        preferences.edit().putFloat("alert_distance", alarmDistance).apply();
    }
    public boolean isAlertMoving(){
        return isFineLocation() && getMoveDistance()>= getAlarmDistance();
    }

    private Boolean notSleep = null;
    boolean isNotSleep(){
        if( notSleep == null ){
            notSleep = preferences.getBoolean("notsleep", true);
        }
        return notSleep;
    }
    void setNotSleep(boolean notSleep){
        this.notSleep = notSleep;
        preferences.edit().putBoolean("notsleep", notSleep).apply();
    }

    private Boolean tracking = null;
    boolean isTracking() {
        if( tracking == null ){
            tracking = preferences.getBoolean("tracking", true);
        }
        return tracking;
    }
    void setTracking(boolean tracking) {
        this.tracking = tracking;
        preferences.edit().putBoolean("tracking", tracking).apply();
    }

    private Boolean useCompress;
    boolean isUseCompress() {
        if( useCompress==null ){
            useCompress = preferences.getBoolean("compress", true);
        }
        return useCompress;
    }

    public void setUseCompress(boolean useCompress) {
        this.useCompress = useCompress;
        preferences.edit().putBoolean("compress", useCompress).apply();
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
