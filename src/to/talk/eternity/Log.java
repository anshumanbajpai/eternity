package to.talk.eternity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log
{
    private static final String NETWORK_LOGS_FILENAME = "network.log";
    private final static String ETERNITY_DIR = "/sdcard/eternity/";

    public static void d(String tag, String message)
    {
        android.util.Log.d(tag, message);
        logMessage(message);
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

    private static void logMessage(String s)
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

    private static void logThrowable(Throwable t)
    {
        try {
            File logFile = getOrCreateLogFile();
            PrintStream ps = new PrintStream(logFile);
            t.printStackTrace(ps);
            ps.close();
        } catch (IOException e) {
            e.printStackTrace();

        }
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

