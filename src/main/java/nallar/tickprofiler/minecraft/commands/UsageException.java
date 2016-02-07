package nallar.tickprofiler.minecraft.commands;

public class UsageException extends RuntimeException {
	public UsageException() {
		super();
	}

	public UsageException(String message) {
		super(message);
	}

	public UsageException(String message, Throwable cause) {
		super(message, cause);
	}

	public UsageException(Throwable cause) {
		super(cause);
	}

	protected UsageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
