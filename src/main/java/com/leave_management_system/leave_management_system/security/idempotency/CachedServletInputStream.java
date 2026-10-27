package com.leave_management_system.leave_management_system.security.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class CachedServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream inputStream;

    public CachedServletInputStream(byte[] cachedBody) {
        this.inputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public boolean isFinished() {
        return inputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        // Not used in synchronous servlet processing
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }
}
