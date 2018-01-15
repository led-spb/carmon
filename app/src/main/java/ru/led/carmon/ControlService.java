package ru.led.carmon;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.IBinder;
import android.util.Log;

public class ControlService extends Service {
    public static final int NOTIFY_ID = 0x23761;

    public static final String START_ACTION    = "ru.led.carmon.START";
    public static final String WAKE_ACTION     = "ru.led.carmon.WAKE";
    public static final String LOCATE_ACTION   = "ru.led.carmon.LOCATE";
    public static final String SLEEP_ACTION    = "ru.led.carmon.SLEEP";

    public static final String POWER_ON        = "ru.led.carmon.POWER_ON";
    public static final String POWER_OFF       = "ru.led.carmon.POWER_OFF";
    public static final String TIMESYNC_ACTION = "ru.led.carmon.TIMESYNC_ACTION";

    private ActionReceiver actionReceiver;
    private BotManager  mBot;
    private BotCommands botCommands;
    private CarState    carState;
    private boolean     started = false;
    private boolean     serviceMode = false;

    private PendingIntent wakeIntent, alarmIntent, locateIntent, sleepIntent, powerOffIntent;

    public PendingIntent getLocateIntent() {
        return locateIntent;
    }
    public PendingIntent getSleepIntent() {
        return sleepIntent;
    }
    public PendingIntent getWakeIntent() {
        return wakeIntent;
    }
    public PendingIntent getAlarmIntent() { return alarmIntent; }
    public PendingIntent getPowerOffIntent() {
        return powerOffIntent;
    }


    public ControlService() {
        actionReceiver = new ActionReceiver(this);
    }

    public CarState getCarState() {
        return carState;
    }

    public static Intent getStartIntent(Context context){
        return getStartIntent(context, false);
    }
    public static Intent getStartIntent(Context context, boolean serviceMode){
        return (new Intent())
                .setClass(context, ControlService.class)
                .setAction( START_ACTION )
                .putExtra("serviceMode", serviceMode);
    }

    public BotManager getBotManager() {
        return mBot;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private void initializeIntents(){
        locateIntent = PendingIntent.getBroadcast( this, 0,
                new Intent(LOCATE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("location", (Integer)2)
                        .putExtra( "network", true ),
                0
        );
        wakeIntent   = PendingIntent.getBroadcast( this, 0,
                new Intent(WAKE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("location", (Integer)0)
                        .putExtra( "network", true ),
                0
        );
        alarmIntent  = PendingIntent.getBroadcast( this, 0,
                new Intent(WAKE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("location", (Integer)2)
                        .putExtra( "network", false ),
                0
        );

        sleepIntent  = PendingIntent.getBroadcast( this, 0, new Intent(SLEEP_ACTION), 0 );
        powerOffIntent = PendingIntent.getBroadcast( this, 0, new Intent(POWER_OFF), 0 );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean serviceMode = intent.getBooleanExtra("serviceMode", false);
        synchronized (this){
            if( isStarted() ){
                if( serviceMode!=isServiceMode() ){
                    stopService();
                }else {
                    return START_STICKY;
                }
            }
            started = true;
        }
        startService( serviceMode );
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (this){
            if( !isStarted()) return;
            started = false;

            stopService();
        }
    }

    private void stopService(){
        Log.d(getClass().getPackage().getName(), "Stopping ControlService");

        unregisterReceiver(actionReceiver);
        actionReceiver.stopLocation(this);

        mBot.waitFinish();

        Log.i(getClass().getPackage().getName(), "ControlService stopped");
        stopForeground(true);
        getCarState().setStatus("stopped");
    }

    private void startService(boolean isServiceMode){
        serviceMode = isServiceMode;

        int versionCode = 0;
        try {
            PackageInfo packInfo = getPackageManager().getPackageInfo( getPackageName(), PackageManager.GET_META_DATA );
            versionCode = packInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // ignore;
        }
        initializeIntents();
        Log.i( getClass().getPackage().getName(), "ControlService started");

        this.carState = ((CarMonApp)getApplication()).getCarState();
        this.carState.setVersionCode( versionCode );

        botCommands = new DefaultCommands( this );

        mBot = new BotManager(
                getApplicationContext(),
                carState,
                botCommands
        );

        IntentFilter filter = new IntentFilter();

        if( !isServiceMode() ) {
            filter.addAction(WAKE_ACTION);
            filter.addAction(LOCATE_ACTION);
            filter.addAction(SLEEP_ACTION);
            filter.addAction(TIMESYNC_ACTION);
            filter.addAction(POWER_ON);
            filter.addAction(POWER_OFF);

            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        }
        filter.addAction( ConnectivityManager.CONNECTIVITY_ACTION );

        registerReceiver(actionReceiver, filter);

        if( !isServiceMode() ) {
            actionReceiver.scheduleWake(this, carState.getNextWakeTime());
            actionReceiver.scheduleLocate(this, carState.getNextLocateTime());
            actionReceiver.scheduleAlarm( this, carState.getNextAlarmTime() );
        }

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

        if( !isServiceMode() ) {
            sendBroadcast(new Intent(LOCATE_ACTION));
        }else{
            this.carState.setStatus("service");
        }
        mBot.sendEvent( String.format("Tracker started in %s mode", isServiceMode()?"service":"regular") );
    }

    public boolean isServiceMode() {
        return serviceMode;
    }

    public boolean isStarted() {
        return started;
    }
}