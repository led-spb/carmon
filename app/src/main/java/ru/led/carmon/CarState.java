package ru.led.carmon;

import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Observable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class CarState extends Observable {
    public static final DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private Location location;

    private boolean MqttConnected = false;
    private int queueLength = 0;
    private String status = "stopped";

    private boolean gpsEnabled;
    private boolean timeSync;
    private int satellites;
    private int satellitesUsed;
    private int timeToFirstFix;

    private boolean hasLocation;
    private boolean hasBattery;

    private long wakeInterval;
    private long idleTimeout;
    private long gpsTimeout;

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

        setLocateTimes(
                preferences.getString("locate_times", "07:00 23:00")
        );
        setWakeInterval(
                preferences.getLong("wake_interval", 1 * 60 * 60 * 1000)
        );
        setIdleTimeout(
                preferences.getLong("idle_timeout", 2 * 60 * 1000)
        );
        setGpsTimeout(
                preferences.getLong("gps_timeout", 5 * 60 * 1000)
        );
        setTimeSync(
                preferences.getBoolean("tyme_sync", false)
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

    private static String interval2String(long interval) {
        interval = interval / 1000 / 60;
        if( interval==0 ){
            return "off";
        }
        StringBuilder res = new StringBuilder();
        if( interval >= 60 ){
            res.append( String.format("%d h", interval /60) );
         }
        if( interval % 60 > 0 ) {
            if( interval >= 60 ) {
                res.append(" ");
            }
            res.append(String.format("%d m", interval % 60) );
        }
        return res.toString();
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

    /*
    public String statusString() {
        StringBuilder builder = new StringBuilder();

        builder.append(
                String.format("Car status %s", dateFormat.format(new Date()))
        ).append("\r\n");
        builder.append(
                String.format("Battery: %d%% (%s) %.1fC %dV", getBatteryLevel(),
                        getBatteryPlugged()>0?"charging":"discharging",
                        (float) getBatteryTemperature() / 10, getBatteryVoltage())
        ).append("\r\n");
        builder.append(
                String.format("Wake: %s", interval2String(getWakeInterval()))
        ).append("\r\n");
        builder.append("Locate times: ").append(locateTimesStr()).append("\r\n");
        builder.append(
                String.format("Timeout: %s", interval2String(getIdleTimeout()))
        ).append("\r\n");
        builder.append(
                String.format("GPS: %s", interval2String(getGpsTimeout()))
        );
        return builder.toString();
    }

    public String locationString() {
        StringBuilder builder = new StringBuilder();

        if( getLocation() == null ){
            return "No location";
        }

        Location loc = getLocation();

        String locationType = loc.getProvider();
        if( loc.getExtras()!=null && loc.getExtras().containsKey( "networkLocationType" ) ){
            locationType = loc.getExtras().getString("networkLocationType");
        }

        builder.append(
                String.format("Location via %s (%.0fm)", locationType, getLocation().getAccuracy())
        ).append("\r\n");

        if( loc.getProvider().equals( LocationManager.GPS_PROVIDER ) ) {
            builder.append(
                    String.format("SAT: %d/%d, TTFF: %d sec", getSatellitesUsed(), getSatellites(), (int) Math.ceil(getTimeToFirstFix() / 1000))
            ).append("\r\n");
        }
        builder.append(
                String.format("Coord: %.5f %.5f", getLocation().getLatitude(), getLocation().getLongitude())
        );
        return builder.toString();
    }*/

    public void beginUpdate(boolean useLoc) {
        if( useLoc ) {
            location = null;
            hasLocation = false;

            satellites = 0;
            satellitesUsed = 0;
            timeToFirstFix = 0;
        }
        hasBattery = false;
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

    public long getWakeInterval() {
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

    public long getGpsTimeout() {
        return gpsTimeout;
    }

    public void setGpsTimeout(long gpsTimeout) {
        this.gpsTimeout = gpsTimeout;

        preferences.edit()
                .putLong("gps_timeout", gpsTimeout )
                .apply();
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
        preferences.edit()
                .putLong("idle_timeout", idleTimeout )
                .apply();
    }

    public boolean hasFullInfo() {
        return hasLocation && hasBattery;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        if (this.location == null) {
            this.location = location;
        }

        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            this.location = location;
        }

        if (location.getProvider().equals(this.location.getProvider()) && location.getAccuracy() < this.location.getAccuracy()) {
            this.location = location;
        }

        float minDistance = 50;
        if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
            minDistance = 500;
        }

        if ( (location.getProvider().equals(LocationManager.GPS_PROVIDER)  || !gpsEnabled) && location.getAccuracy() < minDistance ) {
            Log.i(getClass().getPackage().getName(), "hasLocation");
            this.hasLocation = true;
            setChanged();
            notifyObservers();
        }
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
        this.hasBattery = true;
        setChanged();
        notifyObservers();
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

    public boolean isTimeSync() {
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
