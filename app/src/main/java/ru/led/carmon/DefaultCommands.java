package ru.led.carmon;

import android.content.Intent;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
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
/*                        .putExtra("info", false)
                        .putExtra("sleep", false)*/
                        .putExtra("location", (Integer) 2)
        );
        return null;
    }
    public JSONObject execCell(String... args) throws JSONException {
        getService().sendBroadcast(
                new Intent(ControlService.LOCATE_ACTION)
/*                        .putExtra("info", false)
                        .putExtra("sleep", false)*/
                        .putExtra("location", (Integer) 1)
        );
        return null;
    }

    /*
    public JSONObject execInfo(String... args) throws JSONException {
        getService().sendBroadcast(
                new Intent(ControlService.WAKE_ACTION)
                        .putExtra("info", true)
                        .putExtra("location", (Integer) 0)
        );
        return null;
    }*/

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

    /*
    public JSONObject execLocatetimes(String... args) throws JSONException {
        JSONObject result = new JSONObject();


        StringBuilder w = new StringBuilder();
        for (String s : args) {
            w.append(s).append(" ");
        }
        getService().setLocateTimes(w.toString().trim());
        result.put("text", "Ok" );

        return result;
    }

    public JSONObject execWakeinterval(String... args) throws JSONException{
        JSONObject result = new JSONObject();
        if( args.length==0 ){
            result.put("text", "Need wake interval value in minutes");
        }else{
            Integer interval = Integer.parseInt(args[0]);
            if( interval<1 ){
                result.put("text", "Minimal interval is 1 minute" );
            }else {
                result.put("text", "Ok" );

                getService().setWakeInterval(interval * 60 * 1000);
            }
        }

        return result;
    }

    public JSONObject execTimeout(String... args) throws JSONException {
        JSONObject result = new JSONObject();
        if( args.length==0 ){
            result.put("text", "Need idle timeout value in minutes");
        }else{
            Integer timeout = Integer.parseInt(args[0]);
            if( timeout<0 ){
                result.put("text", "Minimal idle timeout is 0 minutes" );
            }else {
                result.put("text", "Ok" );

                getService().setIdleTimeout(timeout * 60 * 1000);
            }
        }
        return result;
    }

    public JSONObject execGpstimeout(String... args) throws JSONException {
        JSONObject result = new JSONObject();
        if( args.length==0 ){
            result.put("text", "Need gps timeout value in minutes");
        }else{
            Integer timeout = Integer.parseInt(args[0]);
            if ( timeout<0 ) {
                result.put("text", "Minimal gps timeout is 0 minutes" );
            } else {
                result.put("text", "Ok" );

                getService().setGpsTimeout(timeout * 60 * 1000);
            }
        }
        return result;
    }
*/

    @Override
    protected JSONObject onContent(File content) {
        JSONObject message = new JSONObject();

        try {
            if(!updateMode){
                message.put("text", "Not in update mode" );
                return message;
            }

            updateMode = false;
            message.put("text", String.format("Updating from file %s", content.getAbsolutePath()));
            return message;
        } catch (JSONException e) {
            // ignore
        }
        return null;
    }


/*
    public JSONObject execUpdate(String... args) throws JSONException {
        JSONObject result = new JSONObject();
        this.updateMode = true;
        result.put("text", "Ok, send me the update");
        return result;
    }
*/
}
