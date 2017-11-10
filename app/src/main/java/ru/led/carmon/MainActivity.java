package ru.led.carmon;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;

public class MainActivity extends Activity implements View.OnClickListener, Observer {
    private Button btnStart, btnLocate, btnSleep, btnStop, btnConfig, btnTestPowerOn, btnTestPowerOff;
    private TextView stateStatus, stateAlarm, stateLocate, stateWake, stateConnection, stateBattery, stateLocation, stateQueue;
    private CarState state;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = ((CarMonApp)getApplication()).getCarState();

        setContentView(R.layout.activity_main);

        btnStart = (Button) findViewById(R.id.btnStart);
        btnStop  = (Button) findViewById(R.id.btnStop);
        btnLocate = (Button) findViewById(R.id.btnLocate);
        btnConfig = (Button) findViewById(R.id.btnConfig);
        btnSleep = (Button) findViewById(R.id.btnSleep);
        btnTestPowerOn = (Button) findViewById(R.id.btnTestPowerOn);
        btnTestPowerOff = (Button) findViewById(R.id.btnTestPowerOff);

        stateStatus = (TextView) findViewById(R.id.stateStatus);
        stateConnection = (TextView) findViewById( R.id.stateConnection);
        stateQueue = (TextView) findViewById( R.id.stateQueue);
        stateAlarm = (TextView) findViewById( R.id.stateAlarm);
        stateLocate = (TextView) findViewById( R.id.stateLocate);
        stateWake = (TextView) findViewById( R.id.stateWake);
        stateBattery = (TextView) findViewById( R.id.stateBattery);
        stateLocation = (TextView) findViewById( R.id.stateLocation);

        btnStart.setOnClickListener( this );
        btnStop.setOnClickListener( this );
        btnLocate.setOnClickListener( this );
        btnConfig.setOnClickListener( this );
        btnSleep.setOnClickListener( this );

        btnTestPowerOn.setOnClickListener( this );
        btnTestPowerOff.setOnClickListener( this );

        updateUI();
        state.addObserver(this);
    }

    @Override
    public void onClick(View view) {
        if( view == btnStart ){
            startService( ControlService.getStartIntent(this) );
        }
        if( view == btnStop ){
            stopService(ControlService.getStartIntent(this));
        }
        if( view == btnLocate ){
            sendBroadcast(
                    new Intent(ControlService.LOCATE_ACTION)
                            /*.putExtra("sleep", true)*/
                            .putExtra("location", (Integer) 2)
            );
        }
        if( view == btnConfig ){
            startActivity( new Intent(this, ConfigActivity.class) );
        }
        if( view == btnSleep ){
            sendBroadcast(
                    new Intent(ControlService.SLEEP_ACTION)
            );
        }
        if( view == btnTestPowerOn ){
            sendBroadcast( new Intent(ControlService.POWER_ON));
        }
        if( view == btnTestPowerOff ){
            sendBroadcast( new Intent(ControlService.POWER_OFF));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        state.deleteObserver(this);
    }

    @Override
    public void update(Observable observable, Object o) {
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                }
        );
    }

    private void updateUI(){
        stateStatus.setText(
                String.format("Status: %s", state.getStatus())
        );
        stateAlarm.setText( String.format("Alarm: %s",
                state.getNextAlarmTime() != null ?
                        CarState.dateFormat.format(state.getNextAlarmTime().getTime()) : "off"
                )
        );
        stateWake.setText( String.format("Wake: %s",
                state.getNextWakeTime() != null ?
                        CarState.dateFormat.format(state.getNextWakeTime().getTime()) : "off"
                )
        );
        stateLocate.setText(
                    String.format("Locate: %s",
                            state.getNextLocateTime() !=null ? CarState.dateFormat.format(state.getNextLocateTime().getTime()): "off"
                    )
        );

        stateConnection.setText( String.format("Connected: %b", state.isMqttConnected() ) );
        stateQueue.setText(String.format("Queue: %d Track: %s", state.getQueueLength(), state.getTrackSize() ));
        stateBattery.setText(
                String.format("Battery: %d%%", state.getBatteryLevel())
        );

        Location loc = state.getLocation();
        if( loc!=null ){
            stateLocation.setText(
                    String.format( "Location: %s via %s %.5f %.5f ",
                            CarState.dateFormat.format(new Date(loc.getTime())),
                            loc.getProvider(),
                            loc.getLatitude(), loc.getLongitude()
                    )
            );
        }else{
            stateLocation.setText( "Location: n/a" );
        }
    }
}
