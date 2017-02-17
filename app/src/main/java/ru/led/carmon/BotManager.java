package ru.led.carmon;

import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import javax.net.ssl.SSLException;

public class BotManager {
    private boolean mStopRequest = false;
    private String mBotToken;
    private int   requestTimeout = 60;
    private long  updateId = -1;
    private int   botAdmin;
    private int   currentMessageId;

    private SharedPreferences preferences;

    private final ArrayList<JSONObject> mResponseQueue;
    private boolean isRunning;
    private final Object pauseLock = new Object();
    private BotCommands commands;

    private JSONObject makeJsonRequest(String urlString, JSONObject params, int timeout_ms ) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();

        if(requestTimeout>0) {
            conn.setReadTimeout(timeout_ms);
        }
        if( params == null ) {
            conn.setRequestMethod("GET");
            conn.connect();
        }else{
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            OutputStreamWriter wr = new OutputStreamWriter( conn.getOutputStream() );
            wr.write(params.toString());
            wr.flush();

            Log.d(getClass().getPackage().getName(), "request data:" + params.toString() );
        }

        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[2048];

        Reader reader = new InputStreamReader(conn.getInputStream(), "UTF-8");
        while (true) {
            int rs = reader.read(buffer);
            if (rs < 0) {
                break;
            }
            builder.append(buffer, 0, rs);
        }
        reader.close();
        conn.disconnect();
        Log.d( getClass().getPackage().getName(), builder.toString() );

        return new JSONObject( builder.toString() );
    }

    public int getCurrentMessageId() {
        return currentMessageId;
    }

    private void setUpdateId(long updateId){
        this.updateId = updateId;
        this.preferences.edit().putLong( "updateId", updateId ).commit();
    }

    private File makeFileRequest(String urlString ) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        File tmpFile = File.createTempFile("download", "", new File("/data/local/tmp") );
        InputStream is = conn.getInputStream();
        FileOutputStream os = new FileOutputStream(tmpFile);

        byte[] buffer = new byte [2048];
        while(true) {
            int rs = is.read(buffer);
            if(rs<0){
                break;
            }
            os.write(buffer,0, rs);
        }
        os.close();
        is.close();
        conn.disconnect();
        return tmpFile;
    }

    private Runnable mRequestRunnable = new Runnable(){
        @Override
        public void run() {
            Log.i(getClass().getPackage().getName(), "Started bot request thread");
            try {
                while (!mStopRequest) {
                    synchronized (pauseLock){
                        while( !isRunning ){
                            try {
                                pauseLock.wait();
                            }catch(InterruptedException e){
                                // ignore
                            }
                        }
                    }

                    try {
                        JSONObject response = makeJsonRequest(
                                String.format("https://api.telegram.org/bot%s/getUpdates?timeout=%d&offset=%d", mBotToken, requestTimeout, updateId + 1),
                                null,
                                (requestTimeout + 5) * 1000
                        );
                        JSONArray updates = response.getJSONArray("result");

                        for (int i = 0; i < updates.length(); i++) {
                            JSONObject update = updates.getJSONObject(i);
                            setUpdateId( update.optLong("update_id", updateId) );

                            final JSONObject message = update.optJSONObject("message");
                            if (message != null) {
                                JSONObject from = message.optJSONObject("from");
                                if( from != null && from.optLong("id") == botAdmin ){
                                    processCommand(message);
                                }
                            }
                        }

                    } catch( Exception e ){
                        Log.e(getClass().getPackage().getName(), "Error getting update", e);
                        Thread.sleep(5000);
                    }
                }
            }catch( InterruptedException e ){
                // ignore
            }

            Log.i( getClass().getPackage().getName(), "Stopped bot request thread" );
        }
    };


    private Runnable mResponseRunnable = new Runnable (){
        @Override
        public void run() {
            Log.i(getClass().getPackage().getName(), "Started bot response thread" );
            try{
                while(!mStopRequest){
                    synchronized (pauseLock){
                        try{
                            while( !isRunning ){
                                pauseLock.wait();
                            }
                        }catch(InterruptedException e){
                            // ignore
                        }
                    }

                    JSONObject message = null;
                    synchronized (mResponseQueue) {
                        if (!mResponseQueue.isEmpty()) {
                            message = mResponseQueue.remove(0);
                        }
                    }

                    try {
                        if (message != null) {
                            message.put("chat_id", botAdmin);
                            String method = "Message";

                            if (message.has("latitude")) {
                                method = "Location";
                            }

                            if (!message.has("text") || !message.optString("text").equals("")) {
                                String url = String.format("https://api.telegram.org/bot%s/send%s", mBotToken, method);
                                makeJsonRequest(url, message, 0);
                            }
                        } else {
                            Thread.sleep(100);
                        }
                    }catch ( SSLException e1){
                        Log.e( getClass().getPackage().getName(), "Error send message", e1 );
                        sendMessage(message);
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        Log.e(getClass().getPackage().getName(), "Error send message", e );
                        Thread.sleep(5000);
                    }
                }
            }catch(InterruptedException e){
                //ignore
            }
            Log.i(  getClass().getPackage().getName(), "Stopped bot response thread" );
        }
    };



    public BotManager( SharedPreferences preferences, BotCommands commands ){
        this.preferences = preferences;
        this.mResponseQueue = new ArrayList<JSONObject>();

        this.mBotToken = preferences.getString( "botToken", "" );
        this.botAdmin  = preferences.getInt( "botAdmin", 0);
        Log.i( getClass().getPackage().getName(), String.format("botToken=%s botAdmin=%d", mBotToken, botAdmin) );

        this.isRunning    = false;
        this.mStopRequest = false;

        this.commands = commands;
        this.commands.setManager(this);

        Thread mRequestThread = new Thread(mRequestRunnable);
        mRequestThread.start();

        Thread mResponseThread = new Thread(mResponseRunnable);
        mResponseThread.start();
    }

    public void start(){
        Log.i( getClass().getPackage().getName(), "Resuming bot threads" );

        synchronized (pauseLock){
            isRunning = true;
            pauseLock.notifyAll();
        }
    }

    public void stop(){
        Log.i( getClass().getPackage().getName(), "Pausing bot threads" );
        synchronized (pauseLock){
            isRunning = false;
        }
    }


    private void processCommand( JSONObject message ){
        JSONObject response = new JSONObject();
        final int messageId;

        try {
            if( message.has("text") && !message.getString("text").isEmpty() ) {
                if( message.has("message_id") ) {
                    currentMessageId = message.getInt("message_id");
                    messageId = currentMessageId;
                }else{
                    messageId = 0;
                }

                String command = message.getString("text");
                String[] cmd_args = command.split("\\s+");

                final String commandName = cmd_args[0].replace("/", "");

                final String[] args;
                if( cmd_args.length>1 ){
                    args = Arrays.copyOfRange(cmd_args, 1, cmd_args.length);
                }else{
                    args = new String[]{};
                }

                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                JSONObject response = commands.processCommand( commandName, args );
                                if( response!=null ){
                                    if( messageId>0 ) {
                                        try {
                                            response.put("reply_to_message_id", messageId );
                                        } catch (JSONException e){
                                            // ignore
                                        }
                                    }
                                    sendMessage( response );
                                }
                            }
                        }

                ).start();
                return;
            }

            if( message.has("document") ){
                String file_id = message.optJSONObject("document").getString("file_id");

                JSONObject file = makeJsonRequest(
                        String.format("https://api.telegram.org/bot%s/getFile?file_id=%s", mBotToken, file_id), null, 0
                );
                String file_url = String.format("https://api.telegram.org/file/bot%s/%s", mBotToken, file.optJSONObject("result").optString("file_path") );
                File tmpFile = makeFileRequest(file_url);
                response = commands.onContent( tmpFile );
                if( response!=null ){
                    sendMessage( response );
                }

                /*
                response.put("text", String.format("Updating app from file: %s", tmpFile.getAbsolutePath()));
                sendMessage(response);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(tmpFile), "application/vnd.android.package-archive");
                intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
                mService.startActivity(intent);
                */
                return;
            }

            response.put("text", "Unsupported content");
            sendMessage(response);
        } catch (Exception e) {
            Log.e( getClass().getPackage().getName(), "processCommand error", e );
        }
    }



    public void sendMessage(JSONObject message){
        synchronized (mResponseQueue){
            mResponseQueue.add(message);
        }
    }

    public void sendMessage(int messageId, String text){
        if( text==null || text.equals("") ){
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("text", text);
            if(messageId>0){
                message.put("reply_to_message_id", messageId);
            }
            sendMessage(message);
        } catch (JSONException e) {
            // ignore
        }
    }
    public void sendMessage(String text){
        sendMessage(0, text);
    }


    public void sendMessage(Location location) {
        sendMessage(0, location);
    }
    public void sendMessage(int messageId, Location location){
        if( location==null ){
            return;
        }
        JSONObject message = new JSONObject();
        try {
            message.put("longitude", location.getLongitude());
            message.put("latitude", location.getLatitude());
            if(messageId>0){
                message.put("reply_to_message_id", messageId);
            }            sendMessage( message );
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
