package org.starexec.data.security;

import org.starexec.constants.R;
import org.starexec.constants.R.DefaultSettingAttribute;
import org.starexec.data.database.Permissions;
import org.starexec.data.database.Processors;
import org.starexec.data.database.Settings;
import org.starexec.data.database.Users;
import org.starexec.data.to.DefaultSettings;
import org.starexec.data.to.DefaultSettings.SettingType;
import org.starexec.data.to.Permission;
import org.starexec.data.to.Processor;
import org.starexec.data.to.enums.BenchmarkingFramework;
import org.starexec.data.to.enums.ProcessorType;
import org.starexec.util.Validator;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;

/**
 * Security functions for handling DefaultSettings objects
 *
 * @author Eric
 */
public class SettingSecurity {

	/**
	 * Checks whether a user can
	 *
	 * @param userIdOfOwner
	 * @param userIdOfCaller
	 * @return
	 */
	public static boolean canUserAddOrSeeProfile(int userIdOfOwner, int userIdOfCaller) {
		boolean callerIsOwner = (userIdOfOwner == userIdOfCaller);
		boolean callerIsAdmin = GeneralSecurity.hasAdminWritePrivileges(userIdOfCaller);
		return (callerIsOwner || callerIsAdmin) && !Users.isPublicUser(userIdOfCaller);
	}

	/**
	 * @param settingId
	 * @param userIdOfOwner
	 * @param userIdOfCaller
	 * @return
	 */
	public static ValidatorStatusCode canUserSeeProfile(int settingId, int userIdOfOwner, int userIdOfCaller) {
		if (!canUserAddOrSeeProfile(userIdOfOwner, userIdOfCaller)) {
			return new ValidatorStatusCode(false, "You do not have permission to see the given profile.");
		}

		List<DefaultSettings> settings = Settings.getDefaultSettingsVisibleByUser(userIdOfCaller);
		for (DefaultSettings s : settings) {
			if (s.getId() == settingId) {
				return new ValidatorStatusCode(true);
			}
		}
		return new ValidatorStatusCode(
				false, "You do not have permission to see the given profile, or it does not exist");
	}

	/**
	 * Checks whether the given user can modify the settings profile with the given ID. They can do this assuming they
	 * either own the profile or are a leader in the space the profile is associated with
	 *
	 * @param id
	 * @param userId
	 * @return
	 */
	public static ValidatorStatusCode canModifySettings(int id, int userId) throws SQLException {
		DefaultSettings d = Settings.getProfileById(id);
		if (d == null) {
			return new ValidatorStatusCode(false, "The given setting profile could not be found");
		}
		if (d.getType() == SettingType.USER) {
			if (d.getPrimId() != userId && !GeneralSecurity.hasAdminWritePrivileges(userId)) {
				return new ValidatorStatusCode(false, "You may not update default setting profiles of other users");
			}
			if (Users.isPublicUser(userId)) {
				return new ValidatorStatusCode(false, "Settings for guests cannot be updated");
			}
		} else {
			Permission perm = Permissions.get(userId, d.getPrimId());
			if (perm == null || !perm.isLeader()) {
				return new ValidatorStatusCode(
						false, "Only leaders can update settings profiles belonging to communities.");
			}
		}
		return new ValidatorStatusCode(true);
	}

	/**
	 * Checks whether a user can update the default settings (default timeouts, max-memory, etc.) of a community.
	 *
	 * @param id the ID of the DefaultSettings object
	 * @param attribute The name of the setting being changed
	 * @param newValue The new value that would be given to the setting
	 * @param userId The ID of the user making the request
	 * @return 0 if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canUpdateSettings(
			int id, R.DefaultSettingAttribute attribute, String newValue, int userId
	) throws SQLException {
		boolean isInt = Validator.isValidPosInteger(newValue);

		if (attribute == R.DefaultSettingAttribute.CpuTimeout || attribute == DefaultSettingAttribute.ClockTimeout) {
			if (!Validator.isValidPosInteger(newValue)) {
				return new ValidatorStatusCode(false, "The new limit needs to be a valid integer");
			}
			int timeout = Integer.parseInt(newValue);
			if (timeout <= 0) {
				return new ValidatorStatusCode(false, "The new limit needs to be greater than 0");
			}
		} else if (attribute == DefaultSettingAttribute.MaxMem) {
			if (!Validator.isValidPosDouble(newValue)) {
				return new ValidatorStatusCode(false, "The new limit needs to be a valid double");
			}

			double limit = Double.parseDouble(newValue);
			if (limit <= 0) {
				return new ValidatorStatusCode(false, "The new limit needs to be greater than 0");
			}
		} else if (attribute == DefaultSettingAttribute.PostProcess) {
			if (!isInt) {
				return new ValidatorStatusCode(false, "The given processor ID is not valid");
			}
			int procId = Integer.parseInt(newValue);
			if (procId >= 0) {
				if (!ProcessorSecurity.canUserSeeProcessor(Integer.parseInt(newValue), userId).isSuccess()) {
					return new ValidatorStatusCode(
							false,
							"You do not have permission to see the given processor, or the given solver does not exist"
					);
				}
				Processor p = Processors.get(Integer.parseInt(newValue));
				if (p.getType() != ProcessorType.POST) {
					return new ValidatorStatusCode(false, "The given processor is not a preprocessor");
				}
			}
		} else if (attribute == DefaultSettingAttribute.BenchProcess) {
			if (!isInt) {
				return new ValidatorStatusCode(false, "The given processor ID is not valid");
			}
			int procId = Integer.parseInt(newValue);
			if (procId >= 0) {
				if (!ProcessorSecurity.canUserSeeProcessor(Integer.parseInt(newValue), userId).isSuccess()) {
					return new ValidatorStatusCode(
							false,
							"You do not have permission to see the given solver, or the given solver does not exist"
					);
				}
				Processor p = Processors.get(Integer.parseInt(newValue));
				if (p.getType() != ProcessorType.BENCH) {
					return new ValidatorStatusCode(false, "The given processor is not a preprocessor");
				}
			}
		} else if (attribute == DefaultSettingAttribute.defaultbenchmark) {
			if (!isInt) {
				return new ValidatorStatusCode(false, "The given benchmark ID is not valid");
			}
			if (!Permissions.canUserSeeBench(Integer.parseInt(newValue), userId)) {
				return new ValidatorStatusCode(
						false,
						"You do not have permission to see the given benchmark, or the given solver does not exist"
				);
			}
		} else if (attribute == DefaultSettingAttribute.defaultsolver) {
			if (!isInt) {
				return new ValidatorStatusCode(false, "The given solver ID is not valid");
			}
			if (!Permissions.canUserSeeSolver(Integer.parseInt(newValue), userId)) {
				return new ValidatorStatusCode(
						false,
						"You do not have permission to see the given solver, or the given solver does not exist"
				);
			}
		} else if (attribute == DefaultSettingAttribute.PreProcess) {
			if (!isInt) {
				return new ValidatorStatusCode(false, "The given processor ID is not valid");
			}
			int procId = Integer.parseInt(newValue);
			if (procId >= 0) {
				if (!ProcessorSecurity.canUserSeeProcessor(Integer.parseInt(newValue), userId).isSuccess()) {
					return new ValidatorStatusCode(
							false,
							"You do not have permission to see the given solver, or the given solver does not exist"
					);
				}
				Processor p = Processors.get(Integer.parseInt(newValue));
				if (p.getType() != ProcessorType.PRE) {
					return new ValidatorStatusCode(false, "The given processor is not a preprocessor");
				}
			}
		} else if (attribute == DefaultSettingAttribute.BENCHMARKING_FRAMEWORK) {
			boolean isLegalFrameworkName = EnumSet.allOf(BenchmarkingFramework.class).stream()
			                                      .anyMatch(framework -> framework.toString().equals(newValue));

			if (!isLegalFrameworkName) {
				return new ValidatorStatusCode(false, newValue + " is not a legal benchmarking framework name.");
			}
		}

		return canModifySettings(id, userId);
	}
}
