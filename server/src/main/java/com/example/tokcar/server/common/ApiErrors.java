package com.example.tokcar.server.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** REST 오류를 {"error":{"code","message","left"?}} 한 모양으로. */
@RestControllerAdvice
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public static Map<String, Object> body(String code, String message, Integer left) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", code);
        e.put("message", message);
        if (left != null) e.put("left", left);
        return Map.of("error", e);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(body(e.code(), e.getMessage(), e.left()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, Object>> badShape(Exception e) {
        return api(ApiException.badRequest("요청 모양이 맞지 않아요"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return api(new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "이 주소는 " + e.getMethod() + " 로 부를 수 없어요"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noSuchPath(NoResourceFoundException e) {
        return api(ApiException.notFound("주소"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unknown(Exception e) {
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.internalServerError().body(body("INTERNAL", "서버에서 문제가 생겼어요", null));
    }
}
