package ru.led.carmon;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Observable;
import java.util.Properties;

public class BotManager extends Observable implements MqttCallback {
    public static String TOPIC_ROOT = "owntracks/";
    public static String TOPIC_STATE = TOPIC_ROOT+"%s/%s";
    //public static String TOPIC_WILL = TOPIC_ROOT+"%s/%s/msg";
    //public static String TOPIC_EVENT = TOPIC_ROOT+"%s/%s/msg";
    //public static String TOPIC_TRACK = TOPIC_ROOT+"%s/%s/track";
    public static String TOPIC_CMD = TOPIC_ROOT+"%s/%s/cmd";
    public static String TRACKER_ID = "tracker";

    private CarState carState;

    private MqttDefaultFilePersistence dataStore;
    //private MqttAsyncClient mqttClient;
    private MqttClient mqttClient;

    private boolean mStopRequest = false;
    private final ArrayList<JSONObject> mResponseQueue;
    private boolean running;
    private final Object pauseLock = new Object();
    private BotCommands commands;
    private Thread botThread;


    private /*IMqttToken*/ void connectMqtt() throws Exception {
        MqttConnectOptions options = new MqttConnectOptions();

        Properties ssl = new Properties();
        ssl.put("com.ibm.ssl.protocol", "TLSv1");
        options.setSSLProperties(ssl);
        options.setCleanSession(false);
        if( !carState.getMqttUsername().equals("") ) {
            options.setUserName( carState.getMqttUsername() );
        }
        if( !carState.getMqttPassword().equals("") ) {
            options.setPassword( carState.getMqttPassword().toCharArray() );
        }
        /*
        String lwt = String.format( "{\"type\":\"msg\",\"tst\":%d,\"text\":\"\"}" );
        options.setWill(
                String.format(TOPIC_WILL, carState.getMqttClientId(), TRACKER_ID),
                lwt.getBytes(), 1, false
        );*/
        mqttClient.connect(options);
        //return mqttClient.connect(options);
    }

    private void closeMqttConnection(){
        if( mqttClient!=null ){
            try {
                if( mqttClient.isConnected() )
                    mqttClient.disconnect();
                mqttClient.close();
                //dataStore.close();
            } catch (MqttException e) {
                Log.e(getClass().getPackage().getName(), "Close MQTT connection", e);
            }
            finally{
                carState.setMqttConnected(false);
            }
        }
    }

    private void reconnectDelay() throws InterruptedException {
        if( !mStopRequest ){
            Thread.sleep(5000);
        }
    }

    private Runnable botRunnable = new Runnable(){
        @Override
        public void run() {
            Log.i(getClass().getPackage().getName(), "Started bot thread");
            try {

                while( !mStopRequest ){
                    // Wait for network ready
                    synchronized (pauseLock){
                        while( !running){
                            try {
                                pauseLock.wait();
                            }catch(InterruptedException e){
                                // ignore
                            }
                        }
                    }

                    try {
                        Log.i(getClass().getPackage().getName(), "Start MQTT message loop");
                        // connectMqtt().waitForCompletion();
                        connectMqtt();
                        Log.i(getClass().getPackage().getName(), String.format("Connected to MQTT broker %s", carState.getMqttUrl()));

                        mqttClient.subscribe(String.format(TOPIC_CMD, carState.getMqttClientId(), TRACKER_ID), 1);
                        carState.setMqttConnected(true);

                        // Send messages from queue
                        while( !mStopRequest && mqttClient!=null && mqttClient.isConnected() ) {
                            // Get next message from queue
                            JSONObject message = null;
                            int queueSize = 0;
                            synchronized (mResponseQueue){
                                if( !mResponseQueue.isEmpty() ){
                                    message = mResponseQueue.remove(0);
                                    queueSize = mResponseQueue.size();
                                }
                            }

                            if( message!=null ){
                                try{
                                    try {
                                        String topic = message.optString("topic", "");
                                        boolean retain = message.optBoolean("retain", false);
                                        int qos = message.optInt("qos", 1);

                                        Object payload = message.get("payload");
                                        byte[] data = null;
                                        if( payload instanceof JSONObject ) {
                                            // JSONObject payload = message.getJSONObject("payload")
                                            data = ((JSONObject)payload).toString().getBytes();
                                        }else
                                        if( payload instanceof byte[]){
                                            data = (byte[]) payload;
                                        }
                                        mqttClient.publish( topic, data/*payload.toString().getBytes()*/, qos, retain );
                                    } catch (JSONException e) {
                                        Log.e(getClass().getPackage().getName(), "Malformed JSON message", e);
                                    }
                                }catch(Exception e){
                                    // Requeue failed messages
                                    synchronized (mResponseQueue){
                                        mResponseQueue.add(0, message);
                                    }
                                    throw e;
                                }
                                carState.setQueueLength( queueSize );
                            }

                            // Sleep if queue is empty
                            if( message==null && mqttClient!=null && mqttClient.isConnected() ){
                                Thread.sleep(500);
                            }
                        }
                    }catch( Exception e ){
                        Log.e(getClass().getPackage().getName(), "Error MQTT connection", e);
                    }finally{
                        Log.i(getClass().getPackage().getName(), "Finish MQTT message loop");
                        // Pause before reconnect
                        reconnectDelay();
                    }
                }
            }catch( Exception e ){
                Log.e( getClass().getPackage().getName(), "MQTT creation error", e );
            }finally {
                closeMqttConnection();
                Log.i( getClass().getPackage().getName(), "Stopped bot request thread" );
            }
        }
    };

    public BotManager( Context context, CarState carState, BotCommands commands ) {
        this.carState = carState;

        mResponseQueue = new ArrayList<JSONObject>();

        this.running = false;
        this.mStopRequest = false;

        this.commands = commands;
        this.commands.setManager(this);

        try {
            dataStore = new MqttDefaultFilePersistence(
                    context.getCacheDir().getAbsolutePath()
            );
            //mqttClient = new MqttAsyncClient( carState.getMqttUrl(), carState.getMqttClientId(), dataStore );
            mqttClient = new MqttClient( carState.getMqttUrl(), carState.getMqttClientId(), dataStore );
            mqttClient.setCallback( this );

            /*
            DisconnectedBufferOptions options = new DisconnectedBufferOptions();
            options.setBufferEnabled(true);
            options.setBufferSize(50);
            options.setDeleteOldestMessages(true);
            options.setPersistBuffer(true);
            mqttClient.setBufferOpts(options);
            */
        } catch (MqttException e) {
            Log.e( getClass().getPackage().getName(), "Create MQTT client", e );
        }

        botThread = new Thread(botRunnable);
        botThread.start();
    }

    public void finish(){
        mStopRequest = true;
    }

    public void waitFinish(){
        finish();
        try {
            botThread.join(10000 );
        } catch (InterruptedException e) {
            // pass
        }
    }

    public void start(){
        Log.i( getClass().getPackage().getName(), "Resuming bot thread" );
        synchronized (pauseLock){
            running = true;
            pauseLock.notifyAll();
        }
    }

    public void pause(){
        Log.i( getClass().getPackage().getName(), "Pausing bot thread" );
        synchronized (pauseLock){
            running = false;
        }
    }

    private void queueMessage(JSONObject message){
        synchronized (mResponseQueue){
            String topic = message.optString("topic", "");
            Object payload = message.opt("payload");
            String text = "[binary]";

            if( payload instanceof JSONObject ) {
                // JSONObject payload = message.getJSONObject("payload")
                text = ((JSONObject)payload).toString();
            }
            Log.i( getClass().getPackage().getName(), String.format("MQTT message to %s: %s", topic, text) );
            mResponseQueue.add( message );

            carState.setQueueLength(mResponseQueue.size());
        }
    }

    public void sendEvent(boolean retain, Object payload){
        JSONObject msg = new JSONObject();
        try {
            msg.put("topic", String.format( TOPIC_STATE, carState.getMqttClientId(), TRACKER_ID));
            msg.put("retain", retain);

            if( payload instanceof String){
                JSONObject json = new JSONObject();
                json.put("_type", "msg");
                json.put("_ver", carState.getVersionCode() );
                json.put("text", (String) payload );
                json.put("tst", (new Date()).getTime()/1000 );

                msg.put( "payload", json );
            }else {
                msg.put("payload", payload);
            }
            queueMessage(msg);
        }catch (JSONException e){
            // ignore
        }
    }

    public void sendEvent(String message){
        sendEvent(false, message);
    }

    public void sendStatus(JSONObject status){
        sendEvent(true, status);
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.i( getClass().getPackage().getName(), "MQTT broker connection closed", cause );
        carState.setMqttConnected(false);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.i( getClass().getPackage().getName(), String.format("MQTT message from %s: %s", topic, new String(message.getPayload()) ) );
        JSONObject payload = new JSONObject( new String(message.getPayload()) );

        JSONArray jsonArgs = payload.optJSONArray("args");
        ArrayList<String> args = new ArrayList<String>();
        for(int i=0; jsonArgs!=null && i<jsonArgs.length(); i++ ){
            args.add( jsonArgs.getString(i) );
        }
        /*JSONObject result = */commands.processCommand(
                payload.getString("cmd"),
                args.toArray( new String[args.size()] )
        );/*
        if( result!=null ){
            queueMessage( result );
        }*/
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
