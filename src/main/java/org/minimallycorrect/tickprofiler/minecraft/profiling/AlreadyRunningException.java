package org.minimallycorrect.tickprofiler.minecraft.profiling;

public class AlreadyRunningException extends RuntimeException {
	AlreadyRunningException(String message) {
		super(message);
	}
}
