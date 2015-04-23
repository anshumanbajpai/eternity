package to.talk.eternity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;
import org.apache.http.Header;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();
    private final static String LOGTAG = MainActivity.class.getSimpleName();
    private final static String WHATSAPP_PACKAGE = "com.whatsapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                testConnectivity();
            }
        }, 0, 3, TimeUnit.MINUTES);
    }

    private void testConnectivity() {

        SettableFuture<ConnectionMetric> connectionFuture = NetworkClient.connect(Config.DOOR_HOST, Config.DOOR_PORT, Config.USE_SECURE, Config.SOCKET_TIMEOUT, Config.REQUEST_STRING, true, new ConnectionMetric(), Config.PROXY, Config.INVALIDATE_SESSION);
        Futures.addCallback(connectionFuture, new FutureCallback<ConnectionMetric>() {
            @Override
            public void onSuccess(ConnectionMetric connectionMetric) {
                Log.d(LOGTAG, "Door connected");
            }

            @Override
            public void onFailure(Throwable throwable) {

                Log.d(LOGTAG, "Door connection failed : " + throwable);
                Log.d(LOGTAG, "N/w status : " + isConnected());

                if (isConnected()) {

                    Futures.addCallback(makeHttpRequest(), new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void o) {
                            Log.d(LOGTAG, "Http request succeeds ");
                            // App  has reached the state where its unable to connect to door despite of connectivity and http requests are working.
                            int pid = getWhatsappPid();
                            Log.d(LOGTAG, "Whatsapp Pid : " + pid);
                            //kill the whatsapp process, needs root permission
                            if (pid != -1) {
                                killProcess(pid);
                            }

                            startWhatsapp();
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            Log.d(LOGTAG, "Http request fails : " + throwable);
                        }
                    });
                }
            }
        });
    }

    private void startWhatsapp() {
        PackageManager pm = getPackageManager();
        try {
            Intent waIntent = new Intent(Intent.ACTION_SEND);
            waIntent.setType("text/plain");
            String text = "connectivity test msg";
            PackageInfo info = pm.getPackageInfo(WHATSAPP_PACKAGE, PackageManager.GET_META_DATA);
            waIntent.setPackage(WHATSAPP_PACKAGE);
            waIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(waIntent, "Share with"));

        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "WhatsApp not Installed", Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private ListenableFuture<Void> makeHttpRequest() {

        final SettableFuture<Void> future = SettableFuture.create();
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

        asyncHttpClient.get(getApplicationContext(), Config.HTTP_ENDPOINT, new TextHttpResponseHandler() {

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseBody, Throwable error) {
                super.onFailure(statusCode, headers, responseBody, error);
                future.setException(error);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseBody) {
                super.onSuccess(statusCode, headers, responseBody);
                future.set(null);
            }

        });
        return future;
    }

    private int getWhatsappPid() {

        int pid = -1;
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> pidsTask = activityManager.getRunningAppProcesses();

        for (int i = 0; i < pidsTask.size(); i++) {
            if (WHATSAPP_PACKAGE.equals(pidsTask.get(i).processName)) {
                pid = pidsTask.get(i).pid;
                break;
            }
        }

        return pid;
    }

    private static void killProcess(int pid) {

        String killCmd = "kill " + pid;

        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(killCmd + '\n');
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (IOException e) {
            Log.d(LOGTAG, "Exception : " + e);
        } catch (InterruptedException e) {
            Log.d(LOGTAG, "Exception : " + e);
        }
    }


    private boolean isConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }
}
