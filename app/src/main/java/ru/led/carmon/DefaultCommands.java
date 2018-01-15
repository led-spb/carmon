package ru.led.carmon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

@SuppressWarnings("unused")
public class DefaultCommands extends BotCommands {
    private boolean updateMode = false;
    private ControlService service;

    public DefaultCommands(ControlService service) {
        this.service = service;
    }

    public ControlService getService() {
        return service;
    }

    public JSONObject execLocate(String... args) throws JSONException {
        getService().sendBroadcast(
                new Intent(ControlService.LOCATE_ACTION)
                        .putExtra("location", (Integer) 2)
        );
        return null;
    }
    public JSONObject execCell(String... args) throws JSONException {
        getService().sendBroadcast(
                new Intent(ControlService.LOCATE_ACTION)
                        .putExtra("location", (Integer) 1)
        );
        return null;
    }

    public JSONObject execPing(String... args) throws  JSONException{
        getManager().sendEvent("Pong");
        return null;
    }

    public JSONObject execTime(String... args) throws JSONException{
        boolean force = false;
        if( args.length>0 && args[0].equalsIgnoreCase("force") ){
            force = true;
        }

        getService().sendBroadcast(
                new Intent(ControlService.TIMESYNC_ACTION)
                        .putExtra("force", force)
        );
        return null;
    }

    public JSONObject execExec(final String... args) throws JSONException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
                try {
                    Thread.sleep(1000);
                    Process process = builder.start();

                    process.getOutputStream().close();

                    BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    char[] buffer = new char[1024];

                    int rc;
                    while (true) {
                        rc = input.read(buffer);
                        if (rc == -1) break;
                        output.append(buffer, 0, rc);
                    }

                    process.waitFor();
                    getManager().sendEvent( output.toString() );
                } catch (Exception e) {
                    e.printStackTrace();
                    getManager().sendEvent( e.toString() );
                }
            }
        }).start();
        return null;
    }

    public JSONObject execSettings(String... args) throws JSONException {
        SharedPreferences prefs = getService().getCarState().getPreferences();

        JSONObject result = new JSONObject( prefs.getAll() );


        JSONObject payload = new JSONObject();
        payload.put("_type", "settings");
        payload.put("_ver", getService().getCarState().getVersionCode() );
        payload.put("data", result);
        payload.put("tst", (new Date()).getTime()/1000 );

        getManager().sendEvent(false, payload );
        return null;
    }

    public JSONObject execService(String... args) throws JSONException {
        getService().startService( ControlService.getStartIntent(getService(), true) );
        return null;
    }

}
