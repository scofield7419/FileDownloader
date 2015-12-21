package com.liulishuo.filedownloader;

import com.liulishuo.filedownloader.model.FileDownloadStatus;
import com.liulishuo.filedownloader.util.FileDownloadLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jacksgong on 12/21/15.
 */
class FileDownloadList {


    private final static class HolderClass {
        private final static FileDownloadList INSTANCE = new FileDownloadList();
    }

    public static FileDownloadList getImpl() {
        return HolderClass.INSTANCE;
    }

    private ArrayList<BaseDownloadTask> list;

    private FileDownloadList() {
        list = new ArrayList<>();
    }

    public BaseDownloadTask get(final int id) {
        synchronized (list) {
            for (BaseDownloadTask baseDownloadTask : list) {
                // TODO 这里只处理第一个的通知，后面这里需要改id为BaseFileDownloadInternal#toString，有可能第二个error，可能性极低
                // 因为目前只有一种可能到这里，在判断是否第二个在队列是否重复的过程中，上一个还没有添加到下载池中
                if (baseDownloadTask.getDownloadId() == id) {
                    return baseDownloadTask;
                }
            }
        }
        return null;
    }

    public boolean contains(final BaseDownloadTask download) {
        return list.contains(download);
    }

    public BaseDownloadTask[] copy() {
        synchronized (list) {
            // 防止size变化
            BaseDownloadTask[] copy = new BaseDownloadTask[list.size()];
            return list.toArray(copy);
        }
    }

    /**
     * 为了某些目的转移，别忘了回调了
     */
    public void divert(final List<BaseDownloadTask> destination) {
        synchronized (list) {
            synchronized (destination) {
                destination.addAll(list);
            }

            list.clear();
        }
    }

    public boolean removeByWarn(final BaseDownloadTask willRemoveDownload) {
        return remove(willRemoveDownload, FileDownloadStatus.warn);
    }

    public boolean removeByError(final BaseDownloadTask willRemoveDownload) {
        return remove(willRemoveDownload, FileDownloadStatus.error);
    }

    public boolean removeByPaused(final BaseDownloadTask willRemoveDownload) {
        return remove(willRemoveDownload, FileDownloadStatus.paused);
    }

    public boolean removeByCompleted(final BaseDownloadTask willRemoveDownload) {
        return remove(willRemoveDownload, FileDownloadStatus.completed);
    }

    /**
     * @param willRemoveDownload
     * @param removeByStatus     must remove by status {@link com.liulishuo.filedownloader.model.FileDownloadStatus#warn}
     *                           {@link com.liulishuo.filedownloader.model.FileDownloadStatus#paused}
     *                           {@link com.liulishuo.filedownloader.model.FileDownloadStatus#completed}
     *                           {@link com.liulishuo.filedownloader.model.FileDownloadStatus#error}
     * @return
     */
    public boolean remove(final BaseDownloadTask willRemoveDownload, final int removeByStatus) {
        boolean succeed;
        synchronized (list) {
            succeed = list.remove(willRemoveDownload);
        }

        if (succeed) {
            // 抛消息
            switch (removeByStatus) {
                case FileDownloadStatus.warn:
                    willRemoveDownload.getDriver().notifyWarn();
                    break;
                case FileDownloadStatus.error:
                    willRemoveDownload.getDriver().notifyError();
                    break;
                case FileDownloadStatus.paused:
                    willRemoveDownload.getDriver().notifyPaused();
                    break;
                case FileDownloadStatus.completed:
                    Throwable ex = null;
                    try{
                        willRemoveDownload.getDriver().notifyBlockComplete();
                    }catch (Throwable e){
                        ex = e;
                    }

                    if (ex != null) {
                        willRemoveDownload.setStatus(FileDownloadStatus.error);
                        willRemoveDownload.setEx(ex);
                        willRemoveDownload.getDriver().notifyError();
                    } else {
                        willRemoveDownload.getDriver().notifyCompleted();
                    }
                    break;
            }

        } else {
            FileDownloadLog.e(this, "remove error, not exist: %s", willRemoveDownload);
        }

        return succeed;
    }

    public void add(final BaseDownloadTask downloadInternal) {
        ready(downloadInternal);

        // 抛消息
        downloadInternal.getDriver().notifyStarted();
    }

    public void ready(final BaseDownloadTask downloadInternal) {
        synchronized (list) {
            if (list.contains(downloadInternal)) {
                FileDownloadLog.w(this, "already has %s", downloadInternal);
            } else {
                list.add(downloadInternal);
            }
        }
    }
}
