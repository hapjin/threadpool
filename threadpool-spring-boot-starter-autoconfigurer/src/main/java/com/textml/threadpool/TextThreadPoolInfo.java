package com.textml.threadpool;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author psj
 * @date 2019/10/28
 */
public class TextThreadPoolInfo implements Iterable<TextThreadPool.Info> {

    private final List<TextThreadPool.Info> infos;

    public TextThreadPoolInfo(List<TextThreadPool.Info> infos) {
        this.infos = Collections.unmodifiableList(infos);
    }

    @Override
    public Iterator<TextThreadPool.Info> iterator() {
        return infos.iterator();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (TextThreadPool.Info info : infos) {
            sb.append(info.toString());
            sb.append(System.getProperty("line.separator"));
        }
        return sb.toString();
    }
}
