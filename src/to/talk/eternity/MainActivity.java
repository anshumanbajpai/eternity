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
import android.util.Log;
import android.view.View;
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

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
    private static final String NETWORK_DETAILS_FILENAME = "network-details.txt";
    private final static String ETERNITY_DIR = "/sdcard/eternity/";
    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();
    private Button _startCaptureBtn;
    private Button _stopCaptureBtn;
    private TextView _captureStatus;
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
        attachButtonClickListeners();
        copyTcpDumpFile();
        _executor.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                testConnectivity();
            }
        }, 0, 3, TimeUnit.MINUTES);
        _notifier = new Notifier(this);
    }


    private void testConnectivity()
    {

        final ConnectionMetric connectionMetric = new ConnectionMetric();
        SettableFuture<ConnectionMetric> connectionFuture = NetworkClient
            .connect(Config.DOOR_HOST, Config.DOOR_PORT, Config.USE_SECURE, Config.SOCKET_TIMEOUT,
                Config.REQUEST_STRING, true, connectionMetric, Config.PROXY,
                Config.INVALIDATE_SESSION);
        Futures.addCallback(connectionFuture, new FutureCallback<ConnectionMetric>()
        {
            @Override
            public void onSuccess(ConnectionMetric connectionMetric)
            {
                Log.d(LOGTAG, "Door connected");
            }

            @Override
            public void onFailure(Throwable throwable)
            {

                Log.d(LOGTAG, "Door connection failed : " + throwable);
                Log.d(LOGTAG, "N/w status : " + isConnected());

                if (isConnected()) {

                    Futures.addCallback(makeHttpRequest(), new FutureCallback<Void>()
                    {
                        @Override
                        public void onSuccess(Void o)
                        {
                            Log.d(LOGTAG, "Http request succeeds ");
                            // App  has reached the state where its unable to connect to door despite of connectivity and http requests are working.
                            logDeviceInfo(connectionMetric);
                            startDebugging();

                        }

                        @Override
                        public void onFailure(Throwable throwable)
                        {
                            Log.d(LOGTAG, "Http request fails : " + throwable);
                        }
                    });
                }
            }
        });
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

    private void startDebugging()
    {
        logDeviceInfo(null);
        killWhatsapp();
        phoneCallToAlert();
        Log.d(LOGTAG, "starting tcpdump");
        final String captureFilename = getCaptureFilename();
        _tcpDumpProcess = startTcpDump(captureFilename);
        _startCaptureBtn.setEnabled(false);
        _stopCaptureBtn.setEnabled(true);
        sendNotification(captureFilename);
        Log.d(LOGTAG, "starting whatsapp");
        startWhatsapp();
        _executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                stopTcpDump(_tcpDumpProcess);
                _startCaptureBtn.setEnabled(true);
                _stopCaptureBtn.setEnabled(false);
            }
        }, 300, TimeUnit.SECONDS);
    }

    private void logDeviceInfo(ConnectionMetric connectionMetric)
    {
        File directory = new File(ETERNITY_DIR);
        directory.mkdirs();
        File logFile = new File(ETERNITY_DIR + NETWORK_DETAILS_FILENAME);
        ArrayList<String> dnsServers = NetworkUtil.getDNSServers();
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM HH:mm:ss:SSS");
            if (connectionMetric != null) {
                appendToBuffer(buf, sdf, "connection metric: " + connectionMetric.toString());
                Log.d(LOGTAG, "connection metric: " + connectionMetric.toString());
            }
            StringBuilder sb = new StringBuilder();
            sb.append("DNS servers: ");
            for (String s : dnsServers) {
                sb.append(s + " ");
            }
            appendToBuffer(buf, sdf, sb.toString());
            appendToBuffer(buf, sdf, "proxy host: " + NetworkUtil.getProxyHost() + " ,port: " +
                                     NetworkUtil.getProxyPort());
            appendToBuffer(buf, sdf, "custom network info: " + NetworkUtil.getNetworkInfo(this));
            buf.close();
        } catch (IOException e) {
            Log.e("Error in logging: ", e.getMessage(), e);
        }
        Log.d(LOGTAG, "DNS servers: ");
        for (String s : dnsServers) {
            Log.d(LOGTAG, s);
        }
        Log.d(LOGTAG,
            "proxy host: " + NetworkUtil.getProxyHost() + " ,port: " + NetworkUtil.getProxyPort());
        Log.d(LOGTAG, "custom network info: " + NetworkUtil.getNetworkInfo(this));

    }

    private void appendToBuffer(BufferedWriter buf, SimpleDateFormat sdf, String text)
        throws IOException
    {
        buf.append(sdf.format(new Date()) + ": " + text);
        buf.newLine();
    }

    private void phoneCallToAlert()
    {
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + Config.PHONE_NUMBER));
        startActivity(callIntent);
    }

    private void sendNotification(String captureFilename)
    {
        _notifier.notify(
            new NotificationContent(MainActivity.this, "tcpdump capture file: " + captureFilename));
    }

    private void attachButtonClickListeners()
    {
        _startCaptureBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                _startCaptureBtn.setEnabled(false);
                _stopCaptureBtn.setEnabled(true);
                startDebugging();
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

    private void copyFile(InputStream in, OutputStream out) throws IOException
    {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
