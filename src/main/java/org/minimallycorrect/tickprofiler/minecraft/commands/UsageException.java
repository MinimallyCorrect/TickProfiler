package org.minimallycorrect.tickprofiler.minecraft.commands;

class UsageException extends RuntimeException {
	UsageException() {
		super();
	}

	UsageException(String message) {
		super(message);
	}
}
