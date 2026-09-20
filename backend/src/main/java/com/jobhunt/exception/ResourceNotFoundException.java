package com.jobhunt.exception;

/** Thrown when an entity does not exist, or is not owned by the calling user. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException job(Long id) {
        return new ResourceNotFoundException("Job not found with id: " + id);
    }

    public static ResourceNotFoundException resume(Long id) {
        return new ResourceNotFoundException("Resume not found with id: " + id);
    }

    public static ResourceNotFoundException coverLetter(Long id) {
        return new ResourceNotFoundException("Cover letter not found with id: " + id);
    }
}
