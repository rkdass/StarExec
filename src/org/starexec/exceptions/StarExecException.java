package org.starexec.exceptions;

/**
 * The base class for all of our custom exceptions.
 */
public class StarExecException extends Exception {

	public StarExecException(String message, Throwable cause) {
		super(message, cause);
	}

	public StarExecException(String message) {
		super(message);
	}
}
