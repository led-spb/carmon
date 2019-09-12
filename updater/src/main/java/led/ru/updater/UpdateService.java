package led.ru.updater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.quartz.CronExpression;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class UpdateService extends Service {
    private static String UPDATE_ACTION = "ru.led.updater.UPDATE";

    private PendingIntent updateIntent;
    private CronExpression updateExpression = null;

    public static Intent getStartIntent(Context context){
        return (new Intent())
                .setClass(context, UpdateService.class)
                .setAction( UPDATE_ACTION );
    }

    public UpdateService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setAirplaneMode(Integer state){
        try {
            Settings.System.putInt(this.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, state);
            this.sendBroadcast(
                    new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", state)
            );
        }catch(Exception e){
            // ignore
        }
    }
    private int getAirplaneMode(){
        return Settings.System.getInt( this.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
    }

    private void initialize(){
        updateIntent = PendingIntent.getBroadcast(this, 0, new Intent("ru.led.updater.UPDATE"), 0 );

        Date nextUpdateDate = null;
        try {
            updateExpression = new CronExpression("0 0 4 * * ?");
        } catch (ParseException e) {
            Log.e( getClass().getPackage().getName(), "Cron expression parse error", e);
        }
        if( updateExpression!=null ){
            nextUpdateDate = updateExpression.getNextValidTimeAfter( new Date() );
            Log.d( getClass().getPackage().getName(), "Scheduling next update at "+nextUpdateDate.toString());

            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel( updateIntent );
            am.set(AlarmManager.RTC_WAKEUP, nextUpdateDate.getTime(), updateIntent);
        }
    }


    private void checkUpdates(){
        this.airplaneMode = getAirplaneMode();
        if( this.airplaneMode!=0 ) {
            setAirplaneMode(0);
        }

        Thread updateThread = new Thread(updateRunnable);
        updateThread.start();
    }

    private void waitForNetwork(long timeout) throws Exception{
        ConnectivityManager cm =
                (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

        long started = System.currentTimeMillis();
        while( System.currentTimeMillis()-started< timeout ) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if( activeNetwork!=null && activeNetwork.isConnected() ){
                return;
            }
            Thread.sleep(1000 );
        }
        throw new Exception( String.format("Network is not available for %d ms",timeout) );
    }



    private OutputStream readStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int len;

        while( (len=input.read(buffer)) !=-1 ){
            output.write(buffer,0, len);
        }
        return output;
    }

    private void updateApplication(String package_name, String url) throws Exception{
        File downloaded = new File("/sdcard/"+package_name+".apk" );
        Log.i( getClass().getPackage().getName(), String.format("Downloading to %s", downloaded.getAbsolutePath()) );

        HttpURLConnection conn = (HttpURLConnection) (new URL(url)).openConnection();

        FileOutputStream writer = new FileOutputStream(downloaded);
        readStream(conn.getInputStream(), writer);
        writer.flush();
        writer.close();

        Log.i(getClass().getPackage().getName(), "Downloaded ok");
        ProcessBuilder builder = new ProcessBuilder(
                "su", "-c", String.format("pm install -t -r %s",  downloaded.getAbsolutePath())
        ).redirectErrorStream(true);
        Thread.sleep(1000);

        Process process = builder.start();
        process.getOutputStream().close();

        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        char[] buffer = new char[1024];

        int rc;
        while (true) {
            rc = input.read(buffer);
            if (rc == -1) break;
            output.append(buffer, 0, rc);
        }
        process.waitFor();

        Log.i(getClass().getPackage().getName(), output.toString() );
    }

    private int airplaneMode=0;
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i( getClass().getPackage().getName(), "Update check started");
            try {
                // wait for network state is active

                //Thread.sleep(7000);
                Log.i( getClass().getPackage().getName(), "Waiting for network available");
                waitForNetwork(30000 );
                Log.i( getClass().getPackage().getName(), "Network is available, loading updates");

                URL url = new URL("https://led-spb.no-ip.org/.updates/update.json");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

                JSONObject update = new JSONObject( readStream(conn.getInputStream(), new ByteArrayOutputStream() ).toString() );
                Log.d(getClass().getPackage().getName(), "Updates: "+update.toString(2) );


                PackageManager pm = getPackageManager();
                List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                for (Iterator it = update.keys(); it.hasNext(); ) {
                    String app_name = (String) it.next();
                    JSONObject update_info = update.getJSONObject(app_name);
                    try {
                        try {
                            PackageInfo info = pm.getPackageInfo(app_name, 0);
                            int targetVersion = update_info.optInt("version", 0);

                            Log.d( getClass().getPackage().getName(), String.format("Application %s has version %d, target version is %d", app_name, info.versionCode, targetVersion) );
                            if (info.versionCode < targetVersion || update_info.optBoolean("force", false) ) {
                                Log.i(getClass().getPackage().getName(), String.format("Application %s is need to update", app_name));

                                updateApplication(app_name, update_info.getString("path"));
                            } else {
                                Log.i(getClass().getPackage().getName(), String.format("Application %s is up-date", app_name));
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            if( update_info.optBoolean("force", false) ){
                                updateApplication(app_name, update_info.getString("path"));
                            }else {
                                Log.w(getClass().getPackage().getName(), String.format("Application %s is not installed", app_name));
                            }
                        }
                    }catch(Exception e){
                        Log.e( getClass().getPackage().getName(), String.format("Error while updating application %s",app_name), e );
                    }
                }
            } catch (Exception e) {
                Log.e( getClass().getPackage().getName(), "Check updates error", e);
            }
            finally {
                if( airplaneMode!=0 ){
                    setAirplaneMode(1);
                }
                Log.d( getClass().getPackage().getName(), "Update check finished");
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initialize();
        checkUpdates();
        return START_STICKY;
    }
}
