package cnlive.downdemo.db;

import java.util.List;

import cnlive.downdemo.entity.ThreadInfo;

/**ThreadInfo数据访问接口
 *
 */


public interface IThreadDAO {

    /**
     * 插入线程消息
     * @param threadInfo
     */
    void insertThread(ThreadInfo threadInfo);

    /**
     * 删除线程
     * @param url
     */
    void deleteThread(String url);

    /**
     * 更新线程下载进度
     * @param url
     * @param thread_id
     * @param finishded
     */
    void updateThread(String url, int thread_id, long finishded);

    /**
     * 查询文件的线程信息
     * @param url
     * @return
     */
    List<ThreadInfo> getThreads(String url);

    /**
     * 线程信息是否存在
     * @param url
     * @param thread_id
     * @return
     */
    boolean isExists(String url, int thread_id);
}
