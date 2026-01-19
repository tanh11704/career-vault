package com.tpanh.server.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessLogicException extends AppException {
    public BusinessLogicException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
