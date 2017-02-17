package ru.led.carmon;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.startService(ControlService.getStartIntent(this));

        setContentView(R.layout.activity_main);
    }
}
