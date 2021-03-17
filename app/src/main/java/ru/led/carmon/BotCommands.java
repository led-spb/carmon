package ru.led.carmon;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class BotCommands {
    private BotManager manager;


    public BotCommands(){
    }

    public void setManager(BotManager manager) {
        this.manager = manager;
    }

    public BotManager getManager() {
        return manager;
    }

    protected JSONObject processCommand(String command, String... args){
        JSONObject response = null;

        String methodName = "exec"+command.substring(0,1).toUpperCase()+command.substring(1).toLowerCase();
        try {
            StringBuilder trace = new StringBuilder(methodName+"(");
            for(String x: args) {
                trace.append( x ).append(", ");
            }
            trace.append(")");
            Log.i( getClass().getPackage().getName(), trace.toString()  );

            Method m = this.getClass().getMethod(methodName, String[].class);
            response = (JSONObject)m.invoke(this, new Object[]{args});

        } catch (NoSuchMethodException e) {
            Log.e( getClass().getPackage().getName(), "processCommand error", e );
            try {
                response = new JSONObject();
                response.put("text", "Unknown command" );
            } catch (JSONException e1) {
                // ignore
            }
        } catch (InvocationTargetException e) {
            Log.e( getClass().getPackage().getName(), "processCommand error", e );
        } catch (IllegalAccessException e) {
            Log.e( getClass().getPackage().getName(), "processCommand error", e );
        }

        return response;
    }
}
