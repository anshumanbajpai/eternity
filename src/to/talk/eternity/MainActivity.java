package to.talk.eternity;

import android.app.Activity;
import android.os.Bundle;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();

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

            }

            @Override
            public void onFailure(Throwable throwable) {


            }
        });
    }
}
