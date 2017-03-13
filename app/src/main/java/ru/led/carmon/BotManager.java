package ru.led.carmon;

import android.content.Context;
import android.content.SharedPreferences;
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
    public static String TOPIC_EVENT = TOPIC_ROOT+"%s/%s/msg";
    public static String TOPIC_CMD = TOPIC_ROOT+"%s/%s/cmd";
    public static String TRACKER_ID = "tracker";

    private CarState carState;

    private String mqttUrl;
    private String mqttUsername;
    private String mqttPassword;
    private String mqttClientId;

    private MqttDefaultFilePersistence dataStore;
    private MqttClient mqttClient;

    private boolean mStopRequest = false;
    private final ArrayList<JSONObject> mResponseQueue;
    private boolean running;
    private final Object pauseLock = new Object();
    private BotCommands commands;


    private void connectMqtt() throws Exception {
        mqttUrl      = carState.getMqttUrl();
        mqttUsername = carState.getMqttUsername();
        mqttPassword = carState.getMqttPassword();
        mqttClientId = carState.getMqttClientId();

        MqttConnectOptions options = new MqttConnectOptions();

        Properties ssl = new Properties();
        ssl.put("com.ibm.ssl.protocol", "TLSv1");
        options.setSSLProperties(ssl);
        options.setCleanSession(false);
        if( !mqttUsername.equals("") ) {
            options.setUserName( mqttUsername );
        }
        if( !mqttPassword.equals("") ) {
            options.setPassword( mqttPassword.toCharArray() );
        }

        mqttClient = new MqttClient( mqttUrl, mqttClientId, dataStore );
        mqttClient.setCallback(this);
        mqttClient.connect(options);

        carState.setMqttConnected(true);
    }

    private void closeMqttConnection(){
        if( mqttClient!=null ){
            try {
                if( mqttClient.isConnected() )
                    mqttClient.disconnect();
                mqttClient.close();
                mqttClient = null;
                carState.setMqttConnected(false);
            } catch (MqttException e) {
                e.printStackTrace();
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
                    synchronized (pauseLock){
                        while( !running){
                            try {
                                pauseLock.wait();
                            }catch(InterruptedException e){
                            }
                        }
                    }

                    try {
                        connectMqtt();
                        Log.i("carmon", String.format("Connected to MQTT broker %s", mqttUrl));
                        mqttClient.subscribe(String.format(TOPIC_CMD, mqttClientId, TRACKER_ID), 1);

                        // Get messages from queue
                        while( !mStopRequest && mqttClient!=null && mqttClient.isConnected() ) {
                            JSONObject message = null;

                            synchronized (mResponseQueue) {
                                while (!mResponseQueue.isEmpty() && mqttClient!=null && mqttClient.isConnected() ) {
                                    message = mResponseQueue.remove(0);

                                    try {
                                        try {
                                            String type = message.optString("type", "");
                                            JSONObject payload = message.getJSONObject("data");

                                            if (type.equals("event")) {
                                                mqttClient.publish(String.format(TOPIC_EVENT, mqttClientId, TRACKER_ID),
                                                        payload.toString().getBytes(), 1, false
                                                );
                                            }
                                            if (type.equals("status")) {
                                                mqttClient.publish(String.format(TOPIC_STATE, mqttClientId, TRACKER_ID),
                                                        payload.toString().getBytes(), 1, true
                                                );
                                            }
                                        } catch (JSONException e) {
                                            Log.e(getClass().getPackage().getName(), "Malformed JSON message", e);
                                        }
                                    }catch(Exception e){
                                        mResponseQueue.add(0, message);
                                        throw e;
                                    }
                                    carState.setQueueLength(mResponseQueue.size());
                                }
                            }
                            Thread.sleep(500);
                        }
                        // Pause before reconnect
                        reconnectDelay();
                    }catch(Exception e){
                        Log.e(getClass().getPackage().getName(), "Error MQTT connection", e);
                        // Pause before reconnect
                        reconnectDelay();
                    }
                }
            }catch( InterruptedException e ){
            }

            closeMqttConnection();
            Log.i( getClass().getPackage().getName(), "Stopped bot request thread" );
        }
    };

    public BotManager( Context context, CarState carState, BotCommands commands ) {
        this.carState = carState;

        this.dataStore = new MqttDefaultFilePersistence(
                context.getCacheDir().getAbsolutePath()
        );

        mResponseQueue = new ArrayList<JSONObject>();

        this.running = false;
        this.mStopRequest = false;

        this.commands = commands;
        this.commands.setManager(this);

        Thread botThread = new Thread(botRunnable);
        botThread.start();
    }

    public void finish(){
        mStopRequest = true;
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

    private void sendMessage(JSONObject message){
        synchronized (mResponseQueue){
            mResponseQueue.add( message );
            carState.setQueueLength(mResponseQueue.size());
        }
    }

    public void sendEvent(String text){
        JSONObject msg = new JSONObject();
        try {
            JSONObject payload = new JSONObject();
            payload.put("_type", "msg");
            payload.put("text", text);
            payload.put("tst", (new Date()).getTime()/1000 );

            msg.put("type", "event");
            msg.put("data", payload);

            sendMessage(msg);
        } catch (JSONException e) {
        }
    }

    public void sendStatus(JSONObject status){
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "status");
            msg.put("data", status);

            sendMessage(msg);
        } catch (JSONException e) {
        }
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
        JSONObject result = commands.processCommand(
                payload.getString("cmd"),
                args.toArray( new String[args.size()] )
        );
        if( result!=null ){
            sendMessage( result );
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
