package cnlive.downdemo.services;

import android.content.Context;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import cnlive.downdemo.db.IThreadDAO;
import cnlive.downdemo.db.ThreadDAOImpl;
import cnlive.downdemo.entity.FileInfo;
import cnlive.downdemo.entity.ThreadInfo;
import cnlive.downdemo.utils.Util;

/**
 * 下载任务类
 */

public class Downloadtask {
    private FileInfo mFileInfo;
    private IThreadDAO mDao;
    private AtomicLong mFinished=new AtomicLong(0);
    private boolean isPause = false;
    private int mThreadCount =1;
    private List<DownloadThread> mDownloadThreadList;
    public static ExecutorService sThreadPool ;
    private Messenger mMessenger;

    public Downloadtask(Context mContext, Messenger mMessenger, FileInfo mFileInfo, int mThreadCount) {
        this.mMessenger=mMessenger;
        this.mFileInfo = mFileInfo;
        this.mThreadCount = mThreadCount;
        mDao = new ThreadDAOImpl(mContext);
       sThreadPool = Executors.newCachedThreadPool();
    }

    public void download() {
        //读取数据库的线程信息
        List<ThreadInfo> threadInfos = mDao.getThreads(mFileInfo.getUrl());
        ThreadInfo threadInfo = null;
        if (threadInfos.size() == 0) {
            //计算每个线程下载的长度
            long length = mFileInfo.getLength() / mThreadCount;
            for (int i = 0; i < mThreadCount; i++) {
                //创建分段下载线程信息
                threadInfo = new ThreadInfo(i, mFileInfo.getUrl(), length * i, (i + 1) * length - 1, 0);
                //最后一个线程下载剩余所有的长度
                if (i == mThreadCount - 1) {
                    threadInfo.setEnd(mFileInfo.getLength());
                }
                //添加到线程信息集合
                threadInfos.add(threadInfo);
                mDao.insertThread(threadInfo);
            }
        }
        mDownloadThreadList = new ArrayList<>();
        //启动多个线程进行下载
        for (ThreadInfo info : threadInfos) {
            DownloadThread thread = new DownloadThread(info);
            thread.setPriority(Thread.MIN_PRIORITY);
            Downloadtask.sThreadPool.execute(thread);
            mDownloadThreadList.add(thread);
        }
    }

    public void setPause(boolean pause) {
        isPause = pause;
    }

    /**
     * 下载线程
     */
    class DownloadThread extends Thread {
        private ThreadInfo mThreadInfo;
        //线程是否执行完毕
        public boolean isFinished = false;

        public DownloadThread(ThreadInfo mThreadInfo) {
            this.mThreadInfo = mThreadInfo;
        }

        @Override
        public void run() {

            HttpURLConnection connection = null;
            RandomAccessFile randomAccessFile = null;
            InputStream input = null;

            try {
                URL url = new URL(mThreadInfo.getUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("GET");

                //设置下载位置
                long start = mThreadInfo.getStart() + mThreadInfo.getFinished();
                /**
                 * 设置资源请求范围
                 * Server通过请求头中的Range: bytes=0-xxx来判断是否是做Range请求，如果这个值存在而且有效，则只发回请求的那部分文件内容，
                 * 响应的状态码变成206，表示Partial Content，并设置Content-Range。如果无效，则返回416状态码，
                 * 表明Request Range Not Satisfiable.如果不包含Range的请求头，则继续通过常规的方式响应。
                 */
                connection.setRequestProperty("Range", "bytes=" + start + "-" + mThreadInfo.getEnd());

                //设置文件写入位置，和文件下载位置对应
                File file = new File(DownloadService.DOWNLOAD_PATH, mFileInfo.getFilename());
                randomAccessFile = new RandomAccessFile(file, "rwd");
                randomAccessFile.seek(start);

                mFinished.getAndAdd(mThreadInfo.getFinished());

                if (connection.getResponseCode() == HttpURLConnection.HTTP_PARTIAL) {
                    input = connection.getInputStream();
                    byte[] buffer = new byte[1024 * 4];
                    int len = -1;
                    long time = System.currentTimeMillis();
                    while ((len = input.read(buffer)) != -1) {
                        //写入文件
                        randomAccessFile.write(buffer, 0, len);

                        mFinished.getAndAdd(len);
                        //累加每个线程完成的进度
                        mThreadInfo.setFinished(mThreadInfo.getFinished() + len);
                        if (System.currentTimeMillis() - time > 1500) {//每1000ms发送一次广播
                            time= System.currentTimeMillis();
                            //这里踩过的坑，f如果设置为long，那么传入putExtra会被截断，小整数总会被截断为0，使得进度更新失败
                            int f= (int) (mFinished.get()*100/mFileInfo.getLength());

                            Message msg=new Message();
                            msg.what=DownloadService.MSG_UPDATE;
                            msg.arg1= f;
                            msg.arg2=mFileInfo.getId();
                            try {
                                mMessenger.send(msg);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                        if (isPause) {
                            mDao.updateThread(mThreadInfo.getUrl(), mThreadInfo.getId(), mThreadInfo.getFinished());
                            return;
                        }
                    }
                    //标识线程执行完毕
                    isFinished = true;

                    checkAllThreadsFinished();

                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                Util.closeQuietly(input);
                Util.closeQuietly(randomAccessFile);
            }

        }
    }

    /**
     * 判断是否所有线程都执行完毕
     */
    private synchronized void checkAllThreadsFinished() {
        boolean allFinished = true;
        for (DownloadThread thread : mDownloadThreadList) {
            if (!thread.isFinished)
                allFinished = false;
            break;
        }

        if (allFinished) {
            //下载完成后，线程信息就没用了，可以删除
            mDao.deleteThread(mFileInfo.getUrl());
            Message msg=new Message();
            msg.what=DownloadService.MSG_FINISHED;
            msg.obj=mFileInfo;
            Log.i("busysnail","task 下载完成");
            try {
                mMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
