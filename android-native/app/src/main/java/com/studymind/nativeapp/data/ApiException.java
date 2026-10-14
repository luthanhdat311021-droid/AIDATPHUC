package com.studymind.nativeapp.data;

import java.io.IOException;

/** A non-2xx answer from the backend or Supabase, with the server's (Vietnamese) message. */
public class ApiException extends IOException {
    public final int status;
    /** Server error code, e.g. PRO_REQUIRED, QUOTA_EXCEEDED, BILLING_DISABLED */
    public final String code;

    public ApiException(int status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
