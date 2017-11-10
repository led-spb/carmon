package ru.led.carmon;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
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

    public JSONObject execService(String... args) throws JSONException {
        getService().startService( ControlService.getStartIntent(getService(), true) );
        return null;
    }

    private File download(String url) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.connect();

        if( conn.getResponseCode() != HttpURLConnection.HTTP_OK ){
            throw new Exception(String.format("Response code :%d %s", conn.getResponseCode(), conn.getResponseMessage()) );
        }


        InputStream is = u.openStream();
        OutputStream os = getService().openFileOutput("update.apk", Context.MODE_WORLD_READABLE);
        try {
            byte[] buffer = new byte[8192];
            int rx;
            while ((rx = is.read(buffer)) != -1) {
                os.write(buffer, 0, rx);
            }
        }finally {
            if (is != null) is.close();
            if (os != null) os.close();
        }
        return getService().getFileStreamPath("update.apk");
    }

    public JSONObject execUpdate(final String... args) throws JSONException {
        if( args.length<=0 ){
            getManager().sendEvent("Wrong command");
        }

        final String url = args[0];

        new Thread(new Runnable() {
            @Override
            public void run() {

                getManager().sendEvent( String.format("Begin update from %s", url) );
                try {
                    File f = download(url);

                } catch (Exception e) {
                    Log.e(getClass().getPackage().getName(), "Error while downloading update", e);
                    getManager().sendEvent( e.toString() );
                }
            }
        }).start();

        return null;
    }
}
