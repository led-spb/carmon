package ru.led.carmon;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;


public class ConfigActivity extends Activity implements View.OnClickListener {
    private EditText editUrl;
    private EditText editUsername;
    private EditText editPassword;
    private EditText editClientId;

    private EditText editWakeSchedule;
    private EditText editLocateSchedule;
    private EditText editAlarmSchedule;

    private EditText editIdleTimeout;
    private EditText editGpsTimeout;
    private EditText editTrackDistance;
    private EditText editAlarmDistance;

    private CheckBox editTimesync;
    private CheckBox editUseGzip;
    private CheckBox editTracking;
    private CheckBox editNotSleep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CarState state = ((CarMonApp)getApplication()).getCarState();

        setContentView(R.layout.activity_config);

        editUrl = (EditText) findViewById(R.id.editMqttURL);
        editUsername = (EditText) findViewById(R.id.editMqttUsername);
        editPassword = (EditText) findViewById(R.id.editMqttPassword);
        editClientId = (EditText) findViewById(R.id.editMqttClientId);
        editTimesync = (CheckBox) findViewById(R.id.checkBoxTimesync);
        editUseGzip  = (CheckBox) findViewById(R.id.checkUseCompress);
        editTracking = (CheckBox) findViewById(R.id.checkTracking);
        editNotSleep = (CheckBox) findViewById(R.id.checkBoxNotSleep);

        editIdleTimeout = (EditText) findViewById(R.id.editIdleTimeout);
        editGpsTimeout = (EditText) findViewById(R.id.editGpsTimeout);

        editWakeSchedule = (EditText) findViewById(R.id.editWakeSchedule);
        editLocateSchedule = (EditText) findViewById(R.id.editLocateSchedule);
        editAlarmSchedule = (EditText) findViewById(R.id.editAlarmSchedule);

        editTrackDistance = (EditText) findViewById(R.id.editTrackDistance);
        editAlarmDistance = (EditText) findViewById(R.id.editAlarmDistance);


        // set
        editUrl.setText( state.getMqttUrl() );
        editUsername.setText( state.getMqttUsername() );
        editPassword.setText( state.getMqttPassword() );
        editClientId.setText( state.getMqttClientId() );

        editTimesync.setChecked(state.isTimeSync());
        editUseGzip.setChecked(state.isUseCompress());
        editTracking.setChecked(state.isTracking());

        editIdleTimeout.setText(  String.format("%d", state.getIdleTimeout() ) );
        editGpsTimeout.setText( String.format("%d", state.getLocationTimeout() ) );

        editAlarmSchedule.setText( state.getAlarmSchedule() );
        editWakeSchedule.setText( state.getWakeSchedule() );
        editLocateSchedule.setText(  state.getLocateSchedule() );

        editTrackDistance.setText( String.format("%d", (int)state.getTrackDistance()) );
        editAlarmDistance.setText( String.format("%d", (int)state.getAlarmDistance() ) );

        editNotSleep.setChecked( state.isNotSleep() );

        Button btn = (Button) findViewById(R.id.btnSave);
        btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        CarState state = ((CarMonApp)getApplication()).getCarState();

        state.setMqttUrl( editUrl.getText().toString() );
        state.setMqttUsername(editUsername.getText().toString());
        state.setMqttPassword(editPassword.getText().toString());
        state.setMqttClientId(editClientId.getText().toString());

        state.setTimeSync(editTimesync.isChecked());
        state.setGpsEnabled(editUseGzip.isChecked());
        state.setTracking(editTracking.isChecked());
        state.setNotSleep(editNotSleep.isChecked());

        state.setLocateSchedule(editLocateSchedule.getText().toString());
        state.setWakeSchedule( editWakeSchedule.getText().toString() );
        state.setAlarmSchedule( editAlarmSchedule.getText().toString() );

        state.setIdleTimeout(Long.parseLong(editIdleTimeout.getText().toString()) );
        state.setLocationTimeout(Long.parseLong(editGpsTimeout.getText().toString()) );

        state.setAlarmDistance( Float.parseFloat(editAlarmDistance.getText().toString()) );
        state.setTrackDistance( Float.parseFloat(editTrackDistance.getText().toString()) );

        finish();
    }
}
