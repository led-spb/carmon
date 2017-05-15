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
    private boolean     mStarted = false;

    private PendingIntent wakeIntent, locateIntent, sleepIntent, powerOffIntent;

    public PendingIntent getLocateIntent() {
        return locateIntent;
    }
    public PendingIntent getSleepIntent() {
        return sleepIntent;
    }
    public PendingIntent getWakeIntent() {
        return wakeIntent;
    }
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
        return (new Intent()).setClass(context, ControlService.class).setAction( START_ACTION );
    }

    public BotManager getBotManager() {
        return mBot;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        synchronized (this){
            if( ! mStarted ) return;
            mStarted = false;
        }
        Log.i(getClass().getPackage().getName(), "ControlService stopped");

        unregisterReceiver(actionReceiver);
        actionReceiver.stopLocation(this);

        mBot.finish();
        stopForeground(true);
        getCarState().setStatus("stopped");
    }

    private void startService(){
        synchronized (this){
            if( mStarted ) return;
            mStarted = true;
        }

        int versionCode = -1;
        try {
            PackageInfo packInfo = getPackageManager().getPackageInfo( getPackageName(), PackageManager.GET_META_DATA );
            versionCode = packInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // ignore;
        }

        locateIntent = PendingIntent.getBroadcast( this, 0,
                new Intent(LOCATE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("location", (Integer)2),
                0
        );
        wakeIntent   = PendingIntent.getBroadcast( this, 0,
                new Intent(WAKE_ACTION)
                        .putExtra("sleep", true)
                        .putExtra("location", (Integer)0),
                0
        );
        sleepIntent  = PendingIntent.getBroadcast( this, 0, new Intent(SLEEP_ACTION), 0 );
        powerOffIntent = PendingIntent.getBroadcast( this, 0, new Intent(POWER_OFF), 0 );

        Log.i( getClass().getPackage().getName(), "ControlService started");

        this.carState = ((CarMonApp)getApplication()).getCarState();
        botCommands = new DefaultCommands( this );

        mBot = new BotManager(
                getApplicationContext(),
                carState,
                botCommands
        );

        IntentFilter filter = new IntentFilter();

        filter.addAction(WAKE_ACTION);
        filter.addAction(LOCATE_ACTION);
        filter.addAction(SLEEP_ACTION);
        filter.addAction(TIMESYNC_ACTION);
        filter.addAction(POWER_ON);
        filter.addAction(POWER_OFF);

        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);

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
        mBot.sendEvent("Tracker started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startService();
        return START_STICKY;
    }
}
