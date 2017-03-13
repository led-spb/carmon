package ru.led.carmon;

import android.app.Application;
import android.preference.PreferenceManager;

/**
 * Created by Alexey.Ponimash on 13.03.2017.
 */
public class CarMonApp extends Application {
    private CarState carState;

    public CarState getCarState() {
        return carState;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        carState = new CarState( PreferenceManager.getDefaultSharedPreferences(this) );
    }
}
