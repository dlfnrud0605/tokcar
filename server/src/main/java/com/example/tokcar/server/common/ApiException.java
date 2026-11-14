package com.example.tokcar.server.common;

import org.springframework.http.HttpStatus;

/** 앱에 그대로 보여 줄 오류. REST 는 HTTP 상태 + {"error":{…}}, WebSocket 은 {"type":"error",…}로 나간다. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    /** 로그인 남은 시도(BAD_CREDENTIALS 일 때만). */
    private final Integer left;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Integer left) {
        super(message);
        this.status = status;
        this.code = code;
        this.left = left;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Integer left() { return left; }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해 주세요");
    }

    public static ApiException notMember() {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_MEMBER", "이 방의 멤버가 아니에요");
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + "을(를) 찾을 수 없어요");
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
