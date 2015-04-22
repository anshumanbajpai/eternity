package to.talk.eternity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;
import org.apache.http.Header;

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
                                android.os.Process.killProcess(pid);
                            }

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

    private boolean isConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        return connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }
}
