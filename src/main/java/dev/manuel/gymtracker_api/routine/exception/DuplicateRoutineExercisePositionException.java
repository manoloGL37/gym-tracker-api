package dev.manuel.gymtracker_api.routine.exception;

public class DuplicateRoutineExercisePositionException
        extends RuntimeException {

    public DuplicateRoutineExercisePositionException(int position) {
        super("Duplicate exercise position: " + position);
    }
}
