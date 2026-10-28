package com.leave_management_system.leave_management_system.security.idempotency;

import com.leave_management_system.leave_management_system.exception.PayloadTooLargeException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request, long maxBodySizeBytes) throws IOException {
        super(request);
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBodySizeBytes) {
            throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + maxBodySizeBytes + " bytes");
        }

        InputStream is = request.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        long totalRead = 0;
        while ((bytesRead = is.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > maxBodySizeBytes) {
                throw new PayloadTooLargeException("Request body exceeds maximum allowed size of " + maxBodySizeBytes + " bytes");
            }
            baos.write(buffer, 0, bytesRead);
        }
        this.cachedBody = baos.toByteArray();
    }

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        this(request, 1048576L); // 1 MB default
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        Charset charset = getCharacterEncoding() != null ? Charset.forName(getCharacterEncoding()) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody), charset));
    }

    public byte[] getCachedBody() {
        return this.cachedBody;
    }
}
