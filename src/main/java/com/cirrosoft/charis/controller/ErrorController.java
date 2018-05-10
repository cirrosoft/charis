package com.cirrosoft.charis.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ErrorController {

    @ExceptionHandler(Throwable.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> exception(final Throwable throwable) {
        if (throwable == null) return ResponseEntity.ok("ERROR: 500");
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR: 500");
        sb.append("\n");
        sb.append("EXCEPTION-TYPE: ");
        sb.append(throwable.getClass().getName());
        sb.append("\n");
        sb.append("MESSAGE: ");
        sb.append(throwable.getMessage());
        sb.append("\n");
        sb.append("STACKTRACE:");
        for (StackTraceElement trace : throwable.getStackTrace()) {
            sb.append(trace.toString());
            sb.append("\n");
        }
        return ResponseEntity.ok(sb.toString());
    }

}
