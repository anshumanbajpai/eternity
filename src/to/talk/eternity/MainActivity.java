package to.talk.eternity;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import to.talk.eternity.network.ConnectionMetric;
import to.talk.eternity.network.NetworkUtil;
import to.talk.eternity.notifications.NotificationContent;
import to.talk.eternity.notifications.Notifier;

public class MainActivity extends Activity
{

    private final static String LOGTAG = MainActivity.class.getSimpleName();
    private final static String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String NETWORK_LOGS_FILENAME = "network.log";
    private final static String ETERNITY_DIR = "/sdcard/eternity/";
    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();
    private Button _startCaptureBtn;
    private Button _stopCaptureBtn;
    private Button _testConnectivityBtn;
    private TextView _captureStatus;
    private TextView _connectivityStatus;
    private Notifier _notifier;
    private volatile Process _tcpDumpProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        _startCaptureBtn = (Button) findViewById(R.id.startCaptureBtn);
        _stopCaptureBtn = (Button) findViewById(R.id.stopCaptureBtn);
        _captureStatus = (TextView) findViewById(R.id.captureStatus);
        _testConnectivityBtn = (Button) findViewById(R.id.testConnectivityBtn);
        _connectivityStatus = (TextView) findViewById(R.id.connectivityStatus);

        attachButtonClickListeners();
        copyTcpDumpFile();
        _executor.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                testAndDebugIfIssueReproduced();
            }
        }, 0, 3, TimeUnit.MINUTES);
        _notifier = new Notifier(this);
    }


    private void testAndDebugIfIssueReproduced()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                _testConnectivityBtn.setEnabled(false);
                _startCaptureBtn.setEnabled(false);
                _stopCaptureBtn.setEnabled(false);
                _connectivityStatus.setText("");
            }
        });
        final ConnectionMetric connectionMetric = new ConnectionMetric();
        ListenableFuture<Throwable> future = testConnectivity(connectionMetric);
        Futures.addCallback(future, new FutureCallback<Throwable>()
        {
            @Override
            public void onSuccess(final Throwable throwable)
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        refreshConnectvityTestView(throwable);
                    }
                });
                if (throwable != null) {
                    logDeviceInfo(connectionMetric);
                    startDebugging(connectionMetric);
                    informUser(throwable);
                }
            }

            @Override
            public void onFailure(Throwable throwable)
            {

            }
        });

    }

    private void refreshConnectvityTestView(Throwable throwable)
    {
        _testConnectivityBtn.setEnabled(true);
        _startCaptureBtn.setEnabled(true);
        if (throwable == null) {
            _connectivityStatus
                .setText("Either both door and google.com connected or both couldn't connect");
        } else {
            _connectivityStatus.setText(
                "Yay! door couldn't connect and google.com connected, error message: " +
                throwable.getMessage());
        }
    }

    private void informUser(@NotNull Throwable throwable)
    {
        phoneCallToAlert();
        sendNotification(throwable.getMessage());
    }

    private void killWhatsapp()
    {
        int pid = getWhatsappPid();
        Log.d(LOGTAG, "Whatsapp Pid : " + pid);
        //kill the whatsapp process, needs root permission
        if (pid != -1) {
            Log.d(LOGTAG, "killing whatsapp");
            killProcess(pid);
        }
    }

    private void startDebugging(ConnectionMetric connectionMetric)
    {
        killWhatsapp();
        Log.d(LOGTAG, "starting tcpdump");
        final String captureFilename = getCaptureFilename();
        _tcpDumpProcess = startTcpDump(captureFilename);
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                _startCaptureBtn.setEnabled(false);
                _stopCaptureBtn.setEnabled(true);
            }
        });
        Log.d(LOGTAG, "Trying connecting with door");
        ListenableFuture<ConnectionMetric> connectionFuture = connectToDoor(connectionMetric);
        Futures.addCallback(connectionFuture, new FutureCallback<ConnectionMetric>()
        {
            @Override
            public void onSuccess(ConnectionMetric connectionMetric)
            {
                Log.d(LOGTAG, "Door connected");
            }

            @Override
            public void onFailure(final Throwable throwable)
            {

                Log.d(LOGTAG, "Door connection failed : " + throwable);
                Log.d(LOGTAG, "N/w status : " + isConnected());
            }
        });
        Log.d(LOGTAG, "starting whatsapp");
        startWhatsapp();
        _executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                stopTcpDump(_tcpDumpProcess);
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        _startCaptureBtn.setEnabled(true);
                        _stopCaptureBtn.setEnabled(false);
                    }
                });
            }
        }, 120, TimeUnit.SECONDS);
    }

    private ListenableFuture<ConnectionMetric> connectToDoor(ConnectionMetric connectionMetric)
    {
        final SettableFuture<Throwable> future = SettableFuture.create();
        SettableFuture<ConnectionMetric> connectionFuture = NetworkClient
            .connect(Config.DOOR_HOST, Config.DOOR_PORT, Config.USE_SECURE, Config.SOCKET_TIMEOUT,
                Config.REQUEST_STRING, true, connectionMetric, Config.PROXY,
                Config.INVALIDATE_SESSION);
        return connectionFuture;
    }

    private void logDeviceInfo(ConnectionMetric connectionMetric)
    {
        if (connectionMetric != null) {
            Log.d(LOGTAG, "connection metric: " + connectionMetric.toString());
        }

        ArrayList<String> dnsServers = NetworkUtil.getDNSServers();
        StringBuilder sb = new StringBuilder();
        sb.append("DNS servers: ");
        for (String s : dnsServers) {
            sb.append(s + " ");
        }
        Log.d(LOGTAG, sb.toString());
        Log.d(LOGTAG,
            "proxy host: " + NetworkUtil.getProxyHost() + " ,port: " + NetworkUtil.getProxyPort());
        Log.d(LOGTAG, "custom network info: " + NetworkUtil.getNetworkInfo(this));

    }

    private void phoneCallToAlert()
    {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + Config.PHONE_NUMBER));
        startActivity(callIntent);
    }

    private void sendNotification(String captureFilename)
    {
        _notifier.notify(new NotificationContent(MainActivity.this, "error: " + captureFilename));
    }

    private void attachButtonClickListeners()
    {
        _testConnectivityBtn.setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                _testConnectivityBtn.setEnabled(false);
                _startCaptureBtn.setEnabled(false);
                _stopCaptureBtn.setEnabled(false);
                _connectivityStatus.setText("");
                final ConnectionMetric connectionMetric = new ConnectionMetric();
                ListenableFuture<Throwable> future = testConnectivity(connectionMetric);
                Futures.addCallback(future, new FutureCallback<Throwable>()
                {
                    @Override
                    public void onSuccess(final Throwable throwable)
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                refreshConnectvityTestView(throwable);
                            }
                        });

                    }

                    @Override
                    public void onFailure(Throwable throwable)
                    {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                _testConnectivityBtn.setEnabled(true);
                            }
                        });
                    }
                });
            }
        });
        _startCaptureBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                _startCaptureBtn.setEnabled(false);
                _stopCaptureBtn.setEnabled(true);
                logDeviceInfo(null);
                startDebugging(new ConnectionMetric());
                if (_tcpDumpProcess != null) {
                    _captureStatus.setText("Capture in progress");
                } else {
                    _captureStatus.setText("Could not start tcpdump capture");
                }
            }
        });

        _stopCaptureBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                _stopCaptureBtn.setEnabled(false);
                stopTcpDump(_tcpDumpProcess);
                _captureStatus.setText("Capture complete");
                _startCaptureBtn.setEnabled(true);
                NotificationManager nm = (NotificationManager) getSystemService(
                    NOTIFICATION_SERVICE);
                nm.cancelAll();
            }
        });
    }

    private String getCaptureFilename()
    {
        return "tcpdump_" + new SimpleDateFormat("yyyyMMddhhmm'.cap'").format(new Date());
    }

    private static Process startTcpDump(String captureFileName)
    {
        File eternityDir = new File(ETERNITY_DIR);
        eternityDir.mkdir();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("mount -o remount,rw /system\n");
            os.writeBytes("cp /sdcard/Android/data/to.talk.eternity/files/tcpdump system/bin\n");
            os.writeBytes("chmod 777 system/bin/tcpdump\n");
            os.writeBytes("mount -o remount,ro /system\n");
            os.writeBytes(
                "/system/bin/tcpdump -vv -s 0 -w " + ETERNITY_DIR + captureFileName + '\n');
            os.writeBytes("exit\n");
            os.flush();
        } catch (IOException e) {
            Log.e(LOGTAG, "Ex : " + e);
        }

        return p;
    }


    private void stopTcpDump(Process tcpDumpProcess)
    {
        Log.d(LOGTAG, "terminating tcpdump process");
        if (tcpDumpProcess != null) {
            tcpDumpProcess.destroy();
        }
    }

    private void startWhatsapp()
    {
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
            Toast.makeText(this, "WhatsApp not Installed", Toast.LENGTH_SHORT).show();
        }
    }

    private ListenableFuture<Void> makeHttpRequest()
    {

        final SettableFuture<Void> future = SettableFuture.create();
        AsyncHttpClient asyncHttpClient = new AsyncHttpClient();

        asyncHttpClient
            .get(getApplicationContext(), Config.HTTP_ENDPOINT, new TextHttpResponseHandler()
            {

                @Override
                public void onFailure(int statusCode, Header[] headers, String responseBody,
                                      Throwable error)
                {
                    super.onFailure(statusCode, headers, responseBody, error);
                    future.setException(error);
                }

                @Override
                public void onSuccess(int statusCode, Header[] headers, String responseBody)
                {
                    super.onSuccess(statusCode, headers, responseBody);
                    future.set(null);
                }

            });
        return future;
    }

    private int getWhatsappPid()
    {

        int pid = -1;
        ActivityManager activityManager = (ActivityManager) getSystemService(
            Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> pidsTask = activityManager
            .getRunningAppProcesses();

        for (int i = 0; i < pidsTask.size(); i++) {
            if (WHATSAPP_PACKAGE.equals(pidsTask.get(i).processName)) {
                pid = pidsTask.get(i).pid;
                break;
            }
        }

        return pid;
    }

    private static void killProcess(int pid)
    {

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


    private boolean isConnected()
    {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
            CONNECTIVITY_SERVICE);
        return connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null &&
               connectivityManager.getActiveNetworkInfo().isConnected();
    }

    private void copyTcpDumpFile()
    {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e(LOGTAG, "Failed to get asset file list.", e);
        }
        for (String filename : files) {

            if ("tcpdump".equalsIgnoreCase(filename)) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open(filename);
                    File outFile = new File(getExternalFilesDir(null), filename);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                } catch (IOException e) {
                    Log.e(LOGTAG, "Failed to copy asset file: " + filename, e);
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            Log.e(LOGTAG, "Io ex : " + e);
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            Log.e(LOGTAG, "Io ex : " + e);
                        }
                    }
                }
            }
        }
    }

    private ListenableFuture<Throwable> testConnectivity(ConnectionMetric connectionMetric)
    {
        final SettableFuture<Throwable> future = SettableFuture.create();
        ListenableFuture<ConnectionMetric> connectionFuture = connectToDoor(connectionMetric);
        Futures.addCallback(connectionFuture, new FutureCallback<ConnectionMetric>()
        {
            @Override
            public void onSuccess(ConnectionMetric connectionMetric)
            {
                Log.d(LOGTAG, "Door connected");
                future.set(null);
            }

            @Override
            public void onFailure(final Throwable throwable)
            {

                Log.d(LOGTAG, "Door connection failed : " + throwable);
                Log.d(LOGTAG, "N/w status : " + isConnected());

                if (isConnected()) {

                    Futures.addCallback(makeHttpRequest(), new FutureCallback<Void>()
                    {
                        @Override
                        public void onSuccess(Void o)
                        {
                            future.set(throwable);
                            Log.d(LOGTAG, "Http request succeeds ");
                            Log.e(LOGTAG, throwable.getMessage(), throwable);
                        }

                        @Override
                        public void onFailure(Throwable throwable)
                        {
                            Log.d(LOGTAG, "Http request fails");
                            future.set(null);
                        }
                    });
                }
            }
        });
        return future;
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException
    {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
