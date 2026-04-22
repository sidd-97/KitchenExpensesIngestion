package com.cloudkitchen.ingestion.exception;

public class IngestionException extends RuntimeException {
    public IngestionException(String msg) { super(msg); }
    public IngestionException(String msg, Throwable cause) { super(msg, cause); }
}