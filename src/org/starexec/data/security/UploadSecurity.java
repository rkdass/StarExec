package org.starexec.data.security;

import org.starexec.app.RESTHelpers;
import org.starexec.data.database.Uploads;
import org.starexec.data.to.BenchmarkUploadStatus;

import java.sql.SQLException;

/**
 * Determines whether users have authorization to view BenchmarkUploadStatus data
 */
public class UploadSecurity {

	/**
	 * Determines whether a user can see a BenchmarkUploadStatus object that owns the given unvalidated benchmark
	 *
	 * @param userId The ID of the user making the request
	 * @param unvalidatedBenchmarkId The ID of the unvalidated benchmark
	 * @return A ValidatorStatusCode object
	 */
	public static ValidatorStatusCode canViewUnvalidatedBenchmarkOutput(int userId, int unvalidatedBenchmarkId) {
		if (GeneralSecurity.hasAdminReadPrivileges(userId)) {
			return new ValidatorStatusCode(true);
		}
		BenchmarkUploadStatus status = Uploads.getUploadStatusForInvalidBenchmarkId(unvalidatedBenchmarkId);
		if (status != null && status.getUserId() != userId) {
			return new ValidatorStatusCode(false, "You may only view your own benchmark uploads");
		}
		return new ValidatorStatusCode(true);
	}

	/**
	 * Check if uploads are currently frozen
	 * @return True if uploads are currently prohibitted, false otherwise
	 */
	public static boolean uploadsFrozen() {
		try {
			return RESTHelpers.freezePrimitives();
		} catch (SQLException e) {
			return true;
		}
	}
}
