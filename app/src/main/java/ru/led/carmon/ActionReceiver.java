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
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.Calendar;


public class ActionReceiver extends BroadcastReceiver implements LocationListener, GpsStatus.Listener {
    public long POWEROFF_TIMEOUT = 2*60*1000; // 2min
    private ControlService mService;
    private int runCount = 0;
    private long lastTimeSynced = 0;
    private long lastPowerOff = 0;

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
                    intent.getIntExtra("location", 2)
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

        if( intent.getAction().equals(Intent.ACTION_POWER_CONNECTED) ){
            beginPowerAction(context);
            return;
        }
        if( intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED) ){
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

                if( System.currentTimeMillis()-lastTimeSynced >= 7*24*60*60*1000 ) {
                    beginTimeSync(context, true);
                }
            }else if( intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false) ) {
                mService.getBotManager().pause();
            }
            return;
        }
    }

    public void beginTimeSync(Context context, final boolean force){
        if( !mService.getCarState().isTimeSync() )
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
        beginWakeAction(context, false, 2);
        if( System.currentTimeMillis() - lastPowerOff > POWEROFF_TIMEOUT ){
            mService.getBotManager().sendEvent("Ignition is on");
        }
    }

    private void endPowerAction(Context context){
        scheduleSleep(context);
        lastPowerOff = System.currentTimeMillis();
    }

    public void unScheduleSleep(Context context){
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(mService.getSleepIntent());
    }

    public void scheduleSleep(Context context ){
        long timeout = mService.getCarState().getIdleTimeout();

        Log.i( getClass().getPackage().getName(), String.format("Schedule sleep after %d minutes", (int)Math.ceil(timeout/60/1000) ) );
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
            am.set(AlarmManager.RTC_WAKEUP, date.getTimeInMillis(), intent);
        }
    }


    public void scheduleWake(Context context, long interval){
        Log.i(getClass().getPackage().getName(), String.format("Schedule wakeup interval %d minutes", (int) Math.ceil(interval / 60 / 1000)));

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent intent = mService.getWakeIntent();
        am.cancel(intent);

        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, intent);
    }


    private void beginSleepAction(Context context){
        mService.getCarState().setStatus("sleep");
        setAirplaneMode(context, 1);
    }

    private void beginWakeAction(final Context context, final boolean needSleep, final int needLocation){
        synchronized (this){
            runCount++;
        }

        unScheduleSleep(context);
        scheduleLocate(context, mService.getCarState().getNextLocateTime() );
        setAirplaneMode(context, 0);

        mService.getCarState().beginUpdate( needLocation>0 );

        getBatteryInfo(context);
        if( needLocation>0 ){
            mService.getCarState().setStatus("locate");
            startLocation(context, needLocation > 1);
        }else{
            mService.getCarState().setStatus("idle");
        }

        // Thread wait for car location ready
        new Thread (
                new Runnable() {
                    @Override
                    public void run() {
                        CarState state = mService.getCarState();

                        synchronized ( state ){
                            try {
                                JSONObject message = new JSONObject();

                                //if( needInfo || (state.isBatteryWarning() && !state.isBatteryNotified()) ) {
                                    // Status
                                    // mService.getBotManager().sendMessage( requestId, state.statusString() );
                                    message.put("batt", state.getBatteryLevel() );
                                    message.put("charge", state.getBatteryPlugged() );

                                    state.setBatteryNotified(true);
                                //}

                                // Location
                                if( needLocation>0 ) {
                                    // Wait for location
                                    mService.getCarState().wait( mService.getCarState().getGpsTimeout() );
                                    stopLocation(context);
                                }
                                Location loc = state.getLocation();

                                message.put( "cog", loc.getBearing() );
                                message.put( "alt", loc.getAltitude() );
                                message.put( "acc", loc.getAccuracy() );
                                message.put( "lat", loc.getLatitude() );
                                message.put( "lon", loc.getLongitude() );
                                message.put( "vel", loc.getSpeed() );
                                message.put( "tst", loc.getTime()/1000 );

                                message.put( "_type", "location" );

                                mService.getBotManager().sendStatus( message );
                            } catch (Exception e) {
                                Log.e( getClass().getPackage().getName(), "Wait location error", e);
                            }
                            finally {
                                endWakeAction(context, needSleep, needLocation>0);
                            }
                        }
                    }
                }
        ).start();
    }

    private void endWakeAction(Context context, boolean needSleep, boolean needLocation){
        mService.getCarState().setStatus("idle");
        if(needLocation) {
            stopLocation(context);
        }

        synchronized (this) {
            runCount--;
            if (runCount == 0 && needSleep) {
                scheduleSleep(context);
            }
        }
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
        synchronized( state ) {
            state.setBatteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
            state.setBatteryTemperature(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0));
            state.setBatteryVoltage(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0));
            state.setBatteryPlugged( intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0) );

            if( state.hasFullInfo() ){
                state.notifyAll();
            }
        }
    }

    public void startLocation(Context context, boolean useGps){
        LocationManager lm = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );

        mService.getCarState().setGpsEnabled(
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && useGps
        );
        lm.requestLocationUpdates( LocationManager.NETWORK_PROVIDER, 3000, 10, this );
        if( useGps ) {
            lm.requestLocationUpdates( LocationManager.GPS_PROVIDER, 3000, 10, this );
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
        Log.i( getClass().getPackage().getName(), location.toString());

        synchronized (mService.getCarState()) {
            mService.getCarState().setLocation( location );

            if( mService.getCarState().hasFullInfo() ){
                mService.getCarState().notifyAll();
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
        mService.getCarState().setTimeToFirstFix( status.getTimeToFirstFix() );
        mService.getCarState().setSatellites(satellites);
        mService.getCarState().setSatellitesUsed(satellitesInFix);
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
        if( provider.equals(LocationManager.GPS_PROVIDER) ) {
            CarState state = mService.getCarState();

            synchronized (state){
                state.setGpsEnabled(false);
                if( state.getLocation()!=null ) {
                    state.setLocation(state.getLocation());
                    if (state.hasFullInfo()) {
                        state.notifyAll();
                    }
                }
            }
        }
    }
}
