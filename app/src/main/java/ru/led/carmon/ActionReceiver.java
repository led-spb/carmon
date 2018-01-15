package ru.led.carmon;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.GZIPOutputStream;


public class ActionReceiver extends BroadcastReceiver implements LocationListener, GpsStatus.Listener {
    private ControlService mService;
    private int runCount = 0;
    private long lastTimeSynced = 0;
    private long lastPowerOff = 0;
    private PowerManager.WakeLock wakeLock;

    public ActionReceiver(ControlService service){
        mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if( intent==null || intent.getAction()==null )
            return;

        Log.i( getClass().getPackage().getName(), "ActionReceiver.onRecevie: " + intent.getAction() );

        if( intent.getAction().equals( ControlService.WAKE_ACTION) || intent.getAction().equals( ControlService.LOCATE_ACTION ) ){
            beginWakeAction(
                    context,
                    intent.getBooleanExtra("sleep", true),
                    intent.getIntExtra("location", 2),
                    intent.getBooleanExtra("network", true)
            );
            return;
        }

        if( intent.getAction().equals( ControlService.SLEEP_ACTION) ){
            beginSleepAction(context);
            return;
        }

        if( intent.getAction().equals( ControlService.TIMESYNC_ACTION) ){
            beginTimeSync(context,
                    intent.getBooleanExtra("force", false)
            );
            return;
        }

        if( intent.getAction().equals(Intent.ACTION_POWER_CONNECTED) || intent.getAction().equals(ControlService.POWER_ON) ){
            beginPowerAction(context);
            return;
        }
        if( intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED) ){
            lastPowerOff = System.currentTimeMillis();
            schedulePowerOff(context);
            return;
        }

        if( intent.getAction().equals(ControlService.POWER_OFF) ){
            endPowerAction(context);
            return;
        }

        if( intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION) ){
            NetworkInfo ni = (NetworkInfo) intent.getExtras().get(ConnectivityManager.EXTRA_NETWORK_INFO);
            if( ni==null ){
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                ni = cm.getActiveNetworkInfo();
                if( ni== null ){
                    return;
                }
            }
            Log.i(getClass().getPackage().getName(), "isConnected: " + ni.isConnected());
            if( ni.isConnected() ){
                mService.getBotManager().start();

                if( mService.getCarState().isTimeSync() && System.currentTimeMillis()-lastTimeSynced >= 7*24*60*60*1000 ) {
                    beginTimeSync(context, true);
                }
            }else if( intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false) ) {
                mService.getBotManager().pause();
            }
            return;
        }
    }

    private void beginSleepAction(Context context){
        synchronized (this) {
            if (wakeLock != null && wakeLock.isHeld()) {
                Log.i(getClass().getPackage().getName(), "Wakelock end");
                wakeLock.release();
                wakeLock = null;
            }
        }
        mService.getCarState().setStatus("sleep");
        stopLocation(context);
        setAirplaneMode(context, 1);
    }

    private void beginWakeAction(final Context context, final boolean needSleep, final int locationProvider, final boolean network) {
        synchronized (this) {
            runCount++;
            if( wakeLock==null ) {
                wakeLock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, "CarMonitor"
                );
                wakeLock.acquire();
                Log.i( getClass().getPackage().getName(), "Wakelock begin");
            }
        }

        final CarState state = mService.getCarState();

        unScheduleSleep(context);

        scheduleLocate(context, mService.getCarState().getNextLocateTime() );
        scheduleWake(context, mService.getCarState().getNextWakeTime() );
        scheduleAlarm(context, mService.getCarState().getNextAlarmTime() );

        if( network )
            setAirplaneMode(context, 0);
        getBatteryInfo(context);

        if (locationProvider > 0) {
            startLocation(context, locationProvider > 1, true);

            new Thread(
                    new Runnable() {
                        @Override
                        public void run() {
                            synchronized (state) {
                                try {
                                    state.setStatus("locate");
                                    state.wait(state.getGpsTimeout());

                                    if( network || state.isAlertMoving() ) {
                                        if( !network ){
                                            setAirplaneMode(context, 0);
                                        }
                                        mService.getBotManager().sendStatus(state.toJSON());
                                    }
                                } catch (Exception e) {
                                    Log.e(getClass().getPackage().getName(), "Wait location error", e);
                                } finally {
                                    endWakeAction(context, !state.isCharging() && needSleep, !state.isCharging());
                                }
                            }
                        }
                    }
            ).start();
        } else {
            state.setStatus("idle");
            endWakeAction(context, !state.isCharging() && needSleep, false);
        }
    }

    private void endWakeAction(Context context, boolean needSleep, boolean stopLocation){
        if( stopLocation ) {
            stopLocation(context);
        }
        synchronized (this) {
            runCount--;
            if (runCount == 0 && needSleep) {
                mService.getCarState().setStatus("idle");
                scheduleSleep(context);
            }
        }
    }

    public void beginTimeSync(Context context, final boolean force){
        if( !mService.getCarState().isTimeSync() && !force )
            return;

        new Thread(
                new Runnable(){
                    @Override
                    public void run() {
                        NTPUDPClient client = new NTPUDPClient();
                        try {
                            StringBuilder res = new StringBuilder();
                            TimeInfo time = client.getTime(
                                    InetAddress.getByName("ru.pool.ntp.org")
                            );
                            time.computeDetails();

                            long offset = time.getOffset();
                            res.append(String.format("Time delta: %+.4f", (float) offset / 1000 * -1));

                            if( Math.abs(offset)>15000 || force) {
                                SystemClock.setCurrentTimeMillis(System.currentTimeMillis() + offset);

                                time = client.getTime(
                                        InetAddress.getByName("ru.pool.ntp.org")
                                );
                                time.computeDetails();

                                offset = time.getOffset();
                                res.append( String.format(" synced: %+.4f", (float) offset / 1000 * -1) );
                            }else{
                                res.append(" - no need to sync");
                            }
                            lastTimeSynced = System.currentTimeMillis();

                            mService.getBotManager().sendEvent( res.toString() );
                        } catch (Exception e) {
                            StringWriter w = new StringWriter();
                            e.printStackTrace( new PrintWriter(w) );
                            mService.getBotManager().sendEvent( w.toString() );
                        }
                    }
                }
        ).start();
    }

    private void beginPowerAction(Context context){
        unSchedulePowerOff(context);

        beginWakeAction(context, false, 2, true);
        long timeout = mService.getCarState().getPoweroffTimeout();

        if( System.currentTimeMillis() - lastPowerOff > timeout ){
            mService.getBotManager().sendEvent("Ignition is on");
        }
    }

    private void endPowerAction(Context context){
        stopLocation(context);
        scheduleSleep(context);
        getBatteryInfo(context);

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        CarState state = mService.getCarState();

                        synchronized (state) {
                            state.setStatus("idle");
                            // send last know location
                            try {
                                mService.getBotManager().sendStatus(state.toJSON());
                            } catch (JSONException e) {
                                // ignore
                            }

                            // send whole track
                            if( state.isTracking() && state.getTrackSize()>0 ) {
                                try {
                                    // Always append finish point to track
                                    state.addLocationToTrack();

                                    JSONObject track = new JSONObject();
                                    track.put("_type", "track");
                                    track.put("_ver", state.getVersionCode() );
                                    track.put("track", state.getCurrentTrack() );
                                    track.put("tst", (new Date()).getTime()/1000 );

                                    Object trackPayload = track;
                                    if( state.isUseCompress() ) {
                                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                        GZIPOutputStream os = new GZIPOutputStream(bos);
                                        os.write(track.toString().getBytes());
                                        os.finish();
                                        trackPayload = bos.toByteArray();
                                    }

                                    mService.getBotManager().sendEvent(false, trackPayload  );

                                    state.startNewTrack();
                                } catch (Exception e) {
                                    Log.e(getClass().getPackage().getName(), "Error while sending track", e );
                                }
                            }
                        }
                    }
                }
        ).start();
    }

    public void unScheduleSleep(Context context){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(mService.getSleepIntent());
    }

    public void scheduleSleep(Context context ){
        long timeout = mService.getCarState().getIdleTimeout();

        Log.i(getClass().getPackage().getName(), String.format("Schedule sleep after %d minutes", (int) Math.ceil(timeout / 60 / 1000)));
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        PendingIntent intent = mService.getSleepIntent();
        am.cancel(intent);

        if( timeout>0 ){
            am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, intent);
        }
    }

    public void scheduleLocate(Context context, Calendar date){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent intent = mService.getLocateIntent();
        am.cancel(intent);

        if( date!=null) {
            Log.i( getClass().getPackage().getName(), String.format("Schedule locate action at %s",  CarState.dateFormat.format(date.getTime())) );
            am.set(AlarmManager.RTC_WAKEUP, date.getTimeInMillis(), intent);
        }
    }

    public void scheduleWake(Context context, Calendar date){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent intent = mService.getWakeIntent();
        am.cancel(intent);
        if( date!=null ){
            Log.i( getClass().getPackage().getName(), String.format("Schedule wake action at %s", CarState.dateFormat.format(date.getTime())) );
            am.set(AlarmManager.RTC_WAKEUP, date.getTimeInMillis(), intent);
        }
    }

    public void scheduleAlarm(Context context, Calendar date){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent intent = mService.getAlarmIntent();
        am.cancel(intent);
        if( date!=null ){
            Log.i( getClass().getPackage().getName(), String.format("Schedule alarm action at %s", CarState.dateFormat.format(date.getTime())) );
            am.set(AlarmManager.RTC_WAKEUP, date.getTimeInMillis(), intent);
        }
    }

    public void schedulePowerOff(Context context){
        long timeout = mService.getCarState().getPoweroffTimeout();
        Log.i( getClass().getPackage().getName(), String.format("Schedule power off after %d ms", timeout) );
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        am.cancel(mService.getPowerOffIntent());
        am.set( AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + timeout, mService.getPowerOffIntent());
    }
    public void unSchedulePowerOff(Context context){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(mService.getPowerOffIntent());
    }

    private void setAirplaneMode(Context context, Integer state){
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, state);
            context.sendBroadcast(
                    new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", state)
            );
        }catch(Exception e){
            // ignore
        }
    }

    private void getBatteryInfo(Context context){
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED );
        Intent intent = context.registerReceiver(null, filter);

        CarState state = mService.getCarState();

        state.setBatteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
        state.setBatteryTemperature(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0));
        state.setBatteryVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0));
        state.setBatteryPlugged(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
    }

    public void startLocation(Context context, boolean useGps, boolean fast){
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE );

        mService.getCarState().setGpsEnabled(
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && useGps
        );
        lm.requestLocationUpdates( LocationManager.NETWORK_PROVIDER, fast?5000:60*1000, fast?0:500, this );
        if( useGps ) {
            lm.requestLocationUpdates( LocationManager.GPS_PROVIDER, fast?5000:60*1000, fast?0:500, this );
        }
        lm.addGpsStatusListener(this);
    }

    public void stopLocation(Context context){
        LocationManager lm = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );

        lm.removeUpdates(this);
        lm.removeGpsStatusListener(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i( getClass().getPackage().getName(), location.toString() );

        CarState state = mService.getCarState();
        synchronized (state) {
            state.setLocation(location);

            if( state.isFineLocation() ){
                state.notifyAll();
            }
        }
    }

    @Override
    public void onGpsStatusChanged(int event) {
        LocationManager lm = (LocationManager) mService.getSystemService(Context.LOCATION_SERVICE);
        int satellites = 0, satellitesInFix = 0;

        GpsStatus status = lm.getGpsStatus(null);
        for( GpsSatellite sat : status.getSatellites() ){
            satellites++;
            if( sat.usedInFix() ){
                satellitesInFix++;
            }
        }
        CarState state = mService.getCarState();
        state.setTimeToFirstFix(status.getTimeToFirstFix());
        state.setSatellites(satellites);
        state.setSatellitesUsed(satellitesInFix);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String provider) {
        if( provider.equals(LocationManager.GPS_PROVIDER) ){
            mService.getCarState().setGpsEnabled( true );
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if ( provider.equals(LocationManager.GPS_PROVIDER) ) {
            CarState state = mService.getCarState();
            synchronized (state){
                state.setGpsEnabled(false);
                if( state.getLocation()!=null ) {
                    state.setLocation( state.getLocation() );
                    if (state.isFineLocation()) {
                        state.notifyAll();
                    }
                }
            }
        }
    }
}
