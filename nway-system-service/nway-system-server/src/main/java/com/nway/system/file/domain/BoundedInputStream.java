package com.nway.system.file.domain;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 限定最大可读字节数的输入流，用于 HTTP Range 分段读取。
 * <p>
 * 达到上限后返回 -1，不会读到区间外的内容。
 *
 * @author nway
 */
public class BoundedInputStream extends FilterInputStream {

    private long remaining;

    public BoundedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = Math.max(0, limit);
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int b = in.read();
        if (b != -1) {
            remaining--;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int n = in.read(b, off, (int) Math.min(len, remaining));
        if (n > 0) {
            remaining -= n;
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = in.skip(Math.min(n, remaining));
        if (skipped > 0) {
            remaining -= skipped;
        }
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(in.available(), remaining);
    }
}
