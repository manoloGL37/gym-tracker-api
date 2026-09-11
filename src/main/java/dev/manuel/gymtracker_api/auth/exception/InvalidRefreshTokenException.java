package dev.manuel.gymtracker_api.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh session is invalid, expired, or revoked");
    }
}
