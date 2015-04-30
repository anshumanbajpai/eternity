package to.talk.eternity.notifications;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import to.talk.eternity.R;

public class NotificationContent
{
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private final Context _context;
    private String _contentText;
    private PendingIntent _contentIntent;

    public NotificationContent(Context context, String contentText)
    {
        _context = context;
        _contentText = contentText;
    }

    public PendingIntent getContentIntent()
    {
        if (_contentIntent == null) {
            PackageManager pm = _context.getPackageManager();
            try {
                Intent waIntent = new Intent(Intent.ACTION_SEND);
                waIntent.setType("text/plain");
                String text = "connectivity test msg";
                PackageInfo info = pm
                    .getPackageInfo(WHATSAPP_PACKAGE, PackageManager.GET_META_DATA);
                waIntent.setPackage(WHATSAPP_PACKAGE);
                waIntent.putExtra(Intent.EXTRA_TEXT, text);
                _contentIntent = PendingIntent
                    .getActivity(_context, 0, Intent.createChooser(waIntent, "Share with"),
                        PendingIntent.FLAG_UPDATE_CURRENT);

            } catch (PackageManager.NameNotFoundException e) {
                Log.e("WhatsApp not Installed", e.getMessage(), e);
            }
        }
        return _contentIntent;
    }

    public String getContentText()
    {
        return _contentText;
    }

    public void setContentText(String contentText)
    {
        _contentText = contentText;
    }


    public int getSmallIcon()
    {
        return R.drawable.notification_status_bar_icon;
    }
}
