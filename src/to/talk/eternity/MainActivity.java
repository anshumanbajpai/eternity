package to.talk.eternity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.Header;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity
{

    private final static String LOGTAG = MainActivity.class.getSimpleName();
    private final static String WHATSAPP_PACKAGE = "com.whatsapp";
    private final ScheduledExecutorService _executor = Executors.newSingleThreadScheduledExecutor();
    private Button _startCaptureBtn;
    private Button _stopCaptureBtn;
    private volatile Process _tcpDumpProcess;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        _startCaptureBtn = (Button) findViewById(R.id.startCaptureBtn);
        _stopCaptureBtn = (Button) findViewById(R.id.stopCaptureBtn);
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
    }


    private void testConnectivity()
    {

        SettableFuture<ConnectionMetric> connectionFuture = NetworkClient
            .connect(Config.DOOR_HOST, Config.DOOR_PORT, Config.USE_SECURE, Config.SOCKET_TIMEOUT,
                Config.REQUEST_STRING, true, new ConnectionMetric(), Config.PROXY,
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
                            int pid = getWhatsappPid();
                            Log.d(LOGTAG, "Whatsapp Pid : " + pid);
                            //kill the whatsapp process, needs root permission
                            if (pid != -1) {
                                Log.d(LOGTAG, "killing whatsapp");
                                killProcess(pid);
                            }

                            Log.d(LOGTAG, "starting tcpdump");
                            final Process tcpDumpProcess = startTcpDump();
                            Log.d(LOGTAG, "starting whatsapp");
                            startWhatsapp();
                            _executor.schedule(new Runnable()
                            {
                                @Override
                                public void run()
                                {
                                    stopTcpDump(tcpDumpProcess);
                                }
                            }, 30, TimeUnit.SECONDS);

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

    private void attachButtonClickListeners()
    {
        _startCaptureBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                _tcpDumpProcess = startTcpDump();
            }
        });

        _stopCaptureBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                stopTcpDump(_tcpDumpProcess);
            }
        });
    }

    private static Process startTcpDump()
    {

        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("mount -o remount,rw /system\n");
            os.writeBytes("cp /sdcard/Android/data/to.talk.eternity/files/tcpdump system/bin\n");
            os.writeBytes("chmod 777 system/bin/tcpdump\n");
            os.writeBytes("mount -o remount,ro /system\n");
            os.writeBytes(
                "/system/bin/tcpdump -vv -s 0 -w /sdcard/tcpdump_" + (new Date().toString()) +
                ".cap\n");
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
