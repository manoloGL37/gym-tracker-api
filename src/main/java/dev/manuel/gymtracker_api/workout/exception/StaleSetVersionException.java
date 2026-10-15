package dev.manuel.gymtracker_api.workout.exception;

public class StaleSetVersionException extends RuntimeException {
    public StaleSetVersionException() { super("Set changed; fetch current set and reconcile before retrying"); }
}
