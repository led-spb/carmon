package ru.led.carmon;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

        editUrl.setText( state.getMqttUrl() );
        editUsername.setText( state.getMqttUsername() );
        editPassword.setText( state.getMqttPassword() );
        editClientId.setText( state.getMqttClientId() );

        editTimesync.setChecked( state.isTimeSync() );

        editWakeInterval.setText(String.format("%d", state.getWakeInterval() ) );
        editIdleTimeout.setText( String.format("%d", state.getIdleTimeout() ) );
        editLocateTimes.setText(  state.locateTimesStr() );

        Button btn = (Button) findViewById(R.id.btnSave);
        btn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        CarState state = ((CarMonApp)getApplication()).getCarState();
        state.setMqttUrl( editUrl.getText().toString() );
        state.setMqttUsername(editUsername.getText().toString());
        state.setMqttPassword(editPassword.getText().toString());
        state.setMqttClientId(editClientId.getText().toString());
        state.setTimeSync( editTimesync.isChecked() );

        state.setLocateTimes( editLocateTimes.getText().toString() );
        /*
        preferences.edit()
                .putString("mqttUrl",      )
                .putString("mqttUsername",  )
                .putString("mqttPassword",  )
                .putString("mqttClientId", editClientId.getText().toString() )
                .putBoolean("time_sync",    )
                .apply();*/
        finish();
    }
}
