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
    private CheckBox editTimesync;

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
        editWakeSchedule = (EditText) findViewById(R.id.editWakeInterval);
        editIdleTimeout = (EditText) findViewById(R.id.editIdleTimeout);
        editLocateSchedule = (EditText) findViewById(R.id.editLocateTimes);
        editAlarmSchedule = (EditText) findViewById(R.id.editAlertInterval);
        editGpsTimeout = (EditText) findViewById(R.id.editGpsTimeout);

        editUrl.setText( state.getMqttUrl() );
        editUsername.setText( state.getMqttUsername() );
        editPassword.setText( state.getMqttPassword() );
        editClientId.setText( state.getMqttClientId() );

        editTimesync.setChecked(state.isTimeSync());

        editIdleTimeout.setText(  String.format("%d", state.getIdleTimeout() / 1000 / 60) );
        editGpsTimeout.setText( String.format("%d", state.getGpsTimeout() / 1000 / 60 ) );

        editAlarmSchedule.setText( state.getAlarmSchedule() );
        editWakeSchedule.setText( state.getWakeSchedule() );
        editLocateSchedule.setText(  state.getLocateSchedule() );

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

        state.setLocateSchedule(editLocateSchedule.getText().toString());
        state.setWakeSchedule( editWakeSchedule.getText().toString() );
        state.setAlarmSchedule( editAlarmSchedule.getText().toString() );

        state.setIdleTimeout(Long.parseLong(editIdleTimeout.getText().toString()) * 1000 * 60 );
        state.setGpsTimeout(Long.parseLong(editGpsTimeout.getText().toString()) *1000 * 60 );

        finish();
    }
}
