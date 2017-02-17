package ru.led.carmon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StaticReceiver extends BroadcastReceiver {
    public StaticReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent!=null && intent.getAction()!=null  ) {
            if( intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) ){
                context.startService(ControlService.getStartIntent(context));
            }
            if( intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED) ){
                Log.i( getClass().getPackage().getName(), "Application replaced");
                context.startService(ControlService.getStartIntent(context));
            }
        }
    }
}
