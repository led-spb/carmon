package ru.led.carmon;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;


public class ConfigActivity extends Activity implements View.OnClickListener {
    private EditText editUrl;
    private EditText editUsername;
    private EditText editPassword;
    private EditText editClientId;
    private EditText editWakeInterval;
    private EditText editIdleTimeout;
    private EditText editLocateTimes;
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
        editWakeInterval = (EditText) findViewById(R.id.editWakeInterval);
        editIdleTimeout = (EditText) findViewById(R.id.editIdleTimeout);
        editLocateTimes = (EditText) findViewById(R.id.editLocateTimes);
        editGpsTimeout = (EditText) findViewById(R.id.editGpsTimeout);

        editUrl.setText( state.getMqttUrl() );
        editUsername.setText( state.getMqttUsername() );
        editPassword.setText( state.getMqttPassword() );
        editClientId.setText(state.getMqttClientId());

        editTimesync.setChecked(state.isTimeSync());

        editWakeInterval.setText( String.format("%d", state.getWakeInterval() / 1000 / 60));
        editIdleTimeout.setText(  String.format("%d", state.getIdleTimeout() / 1000 / 60) );
        editGpsTimeout.setText( String.format("%d", state.getGpsTimeout() / 1000 / 60 ) );

        editLocateTimes.setText(  state.locateTimesStr() );

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

        state.setLocateTimes(editLocateTimes.getText().toString());

        Log.d("edit", editWakeInterval.getText().toString());

        state.setWakeInterval(Long.parseLong(editWakeInterval.getText().toString()) * 1000 * 60 );
        state.setIdleTimeout(Long.parseLong(editIdleTimeout.getText().toString()) * 1000 * 60 );
        state.setGpsTimeout(Long.parseLong(editGpsTimeout.getText().toString()) *1000 * 60 );

        finish();
    }
}
