package com.myapp.enums;

public enum FileStatus {
    RECEIVED,
    PROCESSING,
    SUCCESS,
    FAILED,
    NOTIFICATION_SENT,          // ← NEW
    NOTIFICATION_FAILED_TO_SEND
}
