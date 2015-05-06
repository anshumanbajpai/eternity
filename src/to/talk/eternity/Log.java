package to.talk.eternity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Log
{
    private final static String NETWORK_LOGS_FILENAME = "network.log";
    private final static String ETERNITY_DIR = "/sdcard/eternity/";
    private final static ScheduledExecutorService _executor = Executors
        .newSingleThreadScheduledExecutor();

    public static void i(String tag, String message)
    {
        android.util.Log.i(tag, message);
        logMessage(message);
    }

    public static void d(String tag, String message)
    {
        d(tag, message, null);
    }

    public static void d(String tag, String message, Throwable t)
    {
        android.util.Log.d(tag, message);
        logMessage(message);
        if (t != null) {
            logThrowable(t);
        }
    }

    public static void e(String tag, String message)
    {
        e(tag, message, null);
    }

    public static void e(String tag, String message, Throwable t)
    {
        android.util.Log.e(tag, message, t);
        logMessage(message);
        if (t != null) {
            logThrowable(t);
        }
    }

    private static void logMessage(final String s)
    {
        _executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    File logFile = getOrCreateLogFile();
                    BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM HH:mm:ss:SSS");
                    buf.append(sdf.format(new Date()) + ": " + s);
                    buf.newLine();
                    buf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, TimeUnit.SECONDS);

    }

    private static void logThrowable(final Throwable t)
    {
        _executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {

                try {
                    File logFile = getOrCreateLogFile();
                    PrintWriter pw = new PrintWriter(
                        new BufferedWriter(new FileWriter(logFile, true)));

                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM HH:mm:ss:SSS");
                    pw.append(sdf.format(new Date()) + ": ");
                    t.printStackTrace(pw);
                    pw.close();
                } catch (IOException e) {
                    e.printStackTrace();

                }
            }
        }, 0, TimeUnit.SECONDS);
    }

    private static File getOrCreateLogFile() throws IOException
    {
        File eternityDir = new File(ETERNITY_DIR);
        eternityDir.mkdirs();
        File logFile = new File(ETERNITY_DIR + NETWORK_LOGS_FILENAME);
        if (!logFile.exists()) {
            logFile.createNewFile();
        }
        return logFile;
    }

}

