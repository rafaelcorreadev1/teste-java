package com.deroyque.activation.shared.persistence;

import org.hibernate.exception.ConstraintViolationException;

public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    public static boolean isViolationOf(Throwable error, String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return constraintName.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
