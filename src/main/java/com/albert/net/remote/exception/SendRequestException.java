package com.albert.net.remote.exception;

public class SendRequestException extends Exception {
    private String addr;

    public SendRequestException(String addr, Throwable cause) {
        super(cause);
        this.addr = addr;
    }

    public SendRequestException(String message) {
        super(message);
    }
}
