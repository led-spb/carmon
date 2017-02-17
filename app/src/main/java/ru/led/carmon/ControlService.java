package ru.led.carmon;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class ControlService extends Service {
    public static final int NOTIFY_ID = 0x23761;

    public static final String START_ACTION    = "ru.led.carmon.START";
    public static final String WAKE_ACTION     = "ru.led.carmon.WAKE";
    public static final String LOCATE_ACTION   = "ru.led.carmon.LOCATE";
    public static final String SLEEP_ACTION    = "ru.led.carmon.SLEEP";
    public static final String TIMESYNC_ACTION = "ri.led.carmon.TIMESYNC_ACTION";

    private ActionReceiver actionReceiver;
    private BotManager  mBot;
    private BotCommands botCommands;
    private CarState    carState;
    private boolean     mStarted = false;

    private PendingIntent wakeIntent, locateIntent, sleepIntent;

    public PendingIntent getLocateIntent() {
        return locateIntent;
    }
    public PendingIntent getSleepIntent() {
        return sleepIntent;
    }

    public PendingIntent getWakeIntent() {
        return wakeIntent;
    }

    public ControlService() {
        actionReceiver = new ActionReceiver(this);
    }

    public CarState getCarState() {
        return carState;
    }

    public static Intent getStartIntent(Context context){
        return (new Intent()).setClass(context, ControlService.class).setAction( START_ACTION );
    }

    public BotManager getBotManager() {
        return mBot;
    }

    /*
    public void setLocateInterval(long interval){
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong("locate_interval", interval)
                .commit();

        carState.setLocateInterval(interval);
        actionReceiver.scheduleLocate(this, carState.getLocateInterval());
    }*/
    public void setLocateTimes(String times){
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("locate_times", times)
                .commit();

        carState.setLocateTimes(times);
        actionReceiver.scheduleLocate(this, carState.getNextLocateTime() );
    }

    public void setIdleTimeout( long timeout ){
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong("idle_timeout", timeout)
                .commit();
        carState.setIdleTimeout(timeout);
        actionReceiver.scheduleSleep(this);
    }

    public void setWakeInterval(long interval){
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong("wake_interval", interval)
                .commit();
        carState.setWakeInterval(interval);

        actionReceiver.scheduleWake(this, carState.getWakeInterval());
    }

    public void setGpsTimeout( long timeout ){
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong("gps_timeout", timeout)
                .commit();
        carState.setGpsTimeout(timeout);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void startService(){
        synchronized (this){
            if( mStarted ) return;
            mStarted = true;
        }

        locateIntent = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(LOCATE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("info",true)
                        .putExtra("location", (Integer)2),
                0
        );
        wakeIntent   = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(WAKE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("info", false)
                        .putExtra("location", (Integer)0),
                0
        );
        sleepIntent  = PendingIntent.getBroadcast(
                this,
                0,
                new Intent(SLEEP_ACTION),
                0
        );


        Log.i( getClass().getPackage().getName(), "ControlService started");


        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.carState = new CarState( preferences );
        this.botCommands = new DefaultCommands( this );
        //mBot = new BotManager("248732991:AAHShyazibjixhfMrYuihMy6iX37FgoCt9E", this, 36329051, botCommands );
        this.mBot = new BotManager( preferences, botCommands );
        /*
        carState.setLocateTimes(
                PreferenceManager.getDefaultSharedPreferences(this).getString("locate_times", "07:00 23:00")
        );
        carState.setWakeInterval(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("wake_interval", 1 * 60 * 60 * 1000)
        );
        carState.setIdleTimeout(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("idle_timeout", 2 * 60 * 1000)
        );
        carState.setGpsTimeout(
                PreferenceManager.getDefaultSharedPreferences(this).getLong("gps_timeout", 5 * 60 * 1000)
        );*/

        IntentFilter filter = new IntentFilter();

        filter.addAction(WAKE_ACTION);
        filter.addAction(LOCATE_ACTION);
        filter.addAction(SLEEP_ACTION);
        filter.addAction(TIMESYNC_ACTION);

        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(actionReceiver, filter);

        actionReceiver.scheduleWake(this, carState.getWakeInterval());
        actionReceiver.scheduleLocate(this, carState.getNextLocateTime() );

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notify = new Notification(
                android.R.drawable.ic_menu_compass,
                getString(R.string.notification_title),
                System.currentTimeMillis()
        );
        notify.setLatestEventInfo(this, getString(R.string.app_name), getString(R.string.notification_title),
                PendingIntent.getActivity(this, 0, new Intent().setClass(this, MainActivity.class), 0)
        );
        nm.notify(NOTIFY_ID, notify);
        startForeground(NOTIFY_ID, notify);

        sendBroadcast(new Intent(LOCATE_ACTION));
        mBot.sendMessage("Service started");
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }
}
