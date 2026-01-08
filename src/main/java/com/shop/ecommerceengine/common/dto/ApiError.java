package com.shop.ecommerceengine.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard error details returned in API error responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private int code;
    private String message;
    private String errorCode;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Map<String, Object> details;
    private String traceId;

    public ApiError() {
        this.timestamp = LocalDateTime.now();
        this.details = new HashMap<>();
    }

    public ApiError(int code, String message) {
        this();
        this.code = code;
        this.message = message;
    }

    public ApiError(int code, String message, String errorCode) {
        this();
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
    }

    public ApiError(int code, String message, String errorCode, Map<String, Object> details) {
        this();
        this.code = code;
        this.message = message;
        this.errorCode = errorCode;
        this.details = details != null ? details : new HashMap<>();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public ApiError addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
}
