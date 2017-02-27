package cnlive.downdemo.utils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import java.util.HashMap;
import java.util.Map;

import cnlive.downdemo.MainActivity;
import cnlive.downdemo.R;
import cnlive.downdemo.entity.FileInfo;

/**
 * 通知工具类
 */

public class NotificationUtil {
    private Context mContext;
    private NotificationManager mNotificationManager;
    //通知集合<文件ID,notification>
    private Map<Integer,Notification> mNotifications;

    public NotificationUtil(Context context) {
        mContext=context.getApplicationContext();
        mNotificationManager= (NotificationManager) mContext.getSystemService(context.NOTIFICATION_SERVICE);
        mNotifications=new HashMap<>();
    }



    /**
     * 显示通知
     * @param fileInfo
     */
    public void showNotification(FileInfo fileInfo){
        Log.i("busysnail","接收MSG_START showNotification"+fileInfo);
        //判断该文件通知是否已经显示,没有的话创建
        if(!mNotifications.containsKey(fileInfo.getId())){
            Notification notification=new Notification();
            //设置滚动文字
            notification.tickerText=fileInfo.getFilename()+" 开始下载";
            //设置显示时间，立即
            notification.when= System.currentTimeMillis();
            //设置图标
            notification.icon=R.drawable.ic_launcher;
            //通知特性，自动消失
            notification.flags= Notification.FLAG_AUTO_CANCEL;
            //点击通知栏的操作
            notification.sound=null;
            Intent intent=new Intent(mContext, MainActivity.class);
            PendingIntent pendingIntent= PendingIntent.getActivity(mContext,0,intent,0);
            notification.contentIntent=pendingIntent;
            //创建RemoteViews
            RemoteViews remoteViews=new RemoteViews(mContext.getPackageName(), R.layout.notification);
            //设置textview
            remoteViews.setTextViewText(R.id.notify_tv_filename,fileInfo.getFilename());
            //将RemoteViews设置到notification
            notification.contentView=remoteViews;
            //发出通知，并加入集合
            mNotificationManager.notify(fileInfo.getId(),notification);
            mNotifications.put(fileInfo.getId(),notification);

        }

    }

    public void cancelNotification(int id){
        mNotificationManager.cancel(id );
        mNotifications.remove(id);
    }

    public void updateNotification(int id,int progress){
        Notification notification=mNotifications.get(id);
        if(notification!=null){
            notification.contentView.setProgressBar(R.id.notify_pb_progressbar,100,progress,false);
            notification.contentView.setTextViewText(R.id.notify_tv_progress,progress+"%");
        }
        //修改完重新发一遍
        mNotificationManager.notify(id,notification);
    }
}