package cnlive.downdemo.utils;


import java.io.Closeable;
import java.io.IOException;


public class Util {

    /**
     * Java 中有一个 Closeable 接口,标识了一个可关闭的对象,它只有一个 close 方法.
     */
    public static void closeQuietly(Closeable s){
        try{
            if(s!=null)
                s.close();
        }catch(IOException e){
            //Log or rethrow as unchecked (like RuntimException) ;)
            e.printStackTrace();
        }
    }

}
