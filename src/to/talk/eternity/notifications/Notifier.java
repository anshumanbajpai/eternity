package to.talk.eternity.notifications;

import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.content.Context;

public class Notifier
{
    private final NotificationManager _notificationManager;
    private final Context _context;

    public Notifier(Context context)
    {
        _notificationManager = (NotificationManager) context
            .getSystemService(Context.NOTIFICATION_SERVICE);
        _context = context;
    }

    public void notify(NotificationContent notificationContent)
    {
        Builder builder = new Builder(_context);
        builder.setContentTitle("Whatsup Whatsapp")
               .setContentText(notificationContent.getContentText())
               .setSmallIcon(notificationContent.getSmallIcon())
               .setContentIntent(notificationContent.getContentIntent()).setAutoCancel(true);

        _notificationManager.notify(1, builder.build());
    }

}
