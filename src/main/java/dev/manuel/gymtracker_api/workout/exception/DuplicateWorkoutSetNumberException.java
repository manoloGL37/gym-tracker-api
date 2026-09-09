package dev.manuel.gymtracker_api.workout.exception;

public class DuplicateWorkoutSetNumberException
        extends RuntimeException {

    private final int setNumber;

    public DuplicateWorkoutSetNumberException(int setNumber) {
        super("A set with number " + setNumber + " already exists");
        this.setNumber = setNumber;
    }

    public int getSetNumber() {
        return setNumber;
    }
}