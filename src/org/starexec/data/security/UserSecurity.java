package org.starexec.data.security;

import java.util.List;

import org.starexec.data.database.Users;
import org.starexec.data.database.Websites;
import org.starexec.data.to.Website;
import org.starexec.data.to.Website.WebsiteType;
import org.starexec.util.Validator;
public class UserSecurity {
	
	/**
	 * Checks to see whether the given website is allowed to be associated with a user
	 * @param name The name to be given the new website
	 * @param URL the URL of the website
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canAssociateWebsite(String name, String URL){
		
		if (!Validator.isValidWebsiteName(name)) {
			return new ValidatorStatusCode(false, "The given name is not formatted correctly. Please refer to the help pages to see the correct format");
		}
		
		if (!Validator.isValidWebsite(URL)) {
			return new ValidatorStatusCode(false, "The given URL is not formatted correctly. Please refer to the help pages to see the correct format");
		}
		return new ValidatorStatusCode(true);
	}
	
	/**
	 * Checks to see whether the given user is allowed to delete a website associated with a space
	 * @param websiteId The ID of the website to be deleted
	 * @param userId The ID of the user who is making the request
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canDeleteWebsite(int userId,int websiteId){
		
		List<Website> websites=Websites.getAll(userId,WebsiteType.USER);
		boolean websiteInSpace=false;
		for (Website w : websites) {
			
			if (w.getId()==websiteId) {
				websiteInSpace=true;
				break;
			}
		}
		
		if (!websiteInSpace) {
			return new ValidatorStatusCode(false, "The given website is not associated with the given user");
		}
		
		return new ValidatorStatusCode(true);
	}
	
	
	
	
	/**
	 * Checks to see whether one user is allowed to delete another
	 * @param userIdBeingDeleted The ID of the user that would be deleted
	 * @param userIdMakingRequest The ID of the user doing the deleting
	 * @return new ValidatorStatusCode(true) if the operation is allowed, and an error code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canDeleteUser(int userIdBeingDeleted, int userIdMakingRequest) {
		if (!Users.isAdmin(userIdMakingRequest) || !Users.isTestUser(userIdBeingDeleted)){
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		
		return new ValidatorStatusCode(true);	
	}
	/**
	 * Checks to see whether a user is allowed to update the data of another user
	 * @param userIdBeingUpdated The ID of the user having their data updated
	 * @param userIdCallingUpdate The ID of the user making the request
	 * @param attribute The name of the attribute being updated. 
	 * @param newVal The new value that will be given to the attribute
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canUpdateData(int userIdBeingUpdated, int userIdCallingUpdate, String attribute, String newVal ){
		boolean admin=Users.isAdmin(userIdCallingUpdate);
		
		if (userIdBeingUpdated!=userIdCallingUpdate && !admin) {
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		
		if (attribute.equals("firstname") || attribute.equals("lastname")) {
			if (!Validator.isValidUserName(newVal)) {
				return new ValidatorStatusCode(false, "The new name is not in the proper format. Please refer to the help pages to see the correct format");
			}
		} else if (attribute.equals("institution")) {
			if (!Validator.isValidInstitution(newVal)) {
				return new ValidatorStatusCode(false, "The new institution is not in the proper format. Please refer to the help pages to see the correct format");
			}
		} else if (attribute.equals("email")) {
			if (!Validator.isValidEmail(newVal)) {
				return new ValidatorStatusCode(false, "The new email is not in the proper format.");
			}
		} else if (attribute.equals("diskquota")) {
			if (!admin) {
				return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
			}
			if (!Validator.isValidLong(newVal)) {
				return new ValidatorStatusCode(false, "The new disk quota is not in the proper format. It must be an integer");
			}

		} else if (attribute.equals("pagesize")) {
			if(!Validator.isValidInteger(newVal) ) {
				return new ValidatorStatusCode(false, "The new disk quota is not in the proper format. It must be an integer");
			}
			int n=Integer.parseInt(newVal);
			if (n<1 || n> 100) {
				return new ValidatorStatusCode(false, "The new disk quota is not in the proper format. It must be between 1 and 100");
			}
		} else {
			return new ValidatorStatusCode(false, "The given attribute does not exist");
		}
		return new ValidatorStatusCode(true);
	}

	public static ValidatorStatusCode canAddOrRemoveDeveloperRole(int userId) {
		if (!Users.isAdmin(userId)) {
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		return new ValidatorStatusCode(true);
	}
	
	/**
	 * Checks to see whether the given user can suspend or reinstate users
	 * @param userIdMakingRequest The ID of the user making the request
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canUserSuspendOrReinstateUser(int userIdMakingRequest) {
		if (!Users.isAdmin(userIdMakingRequest)){
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		return new ValidatorStatusCode(true);
	}

	/**
	 * Checks to see whether the given user can subscribe or unsubscribe users from the report e-mails
	 * @param userIdMakingRequest The Id of the user making the request
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCode otherwise
	 * @author Albert Giegerich
	 */
	public static ValidatorStatusCode canUserSubscribeOrUnsubscribeUser(int userIdMakingRequest) {
		if (!Users.hasAdminWritePrivileges(userIdMakingRequest)){
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		return new ValidatorStatusCode(true);
	}

	public static ValidatorStatusCode canUserGrantOrSuspendDeveloperPrivileges(int userId) {
		if (!Users.hasAdminWritePrivileges(userId)){
			return new ValidatorStatusCode(false, "You do not have permission to perform the requested operation");
		}
		return new ValidatorStatusCode(true);
	}


	/**
	 * Checks to see whether a given user is allowed to see the primitives owned by another user
	 * @param ownerId The ID of the user who owns the primitives
	 * @param requestUserId The ID of the user making the request
	 * @return new ValidatorStatusCode(true) if the operation is allowed and a status code from ValidatorStatusCodes otherwise
	 */
	public static ValidatorStatusCode canViewUserPrimitives(int ownerId, int requestUserId){
		if (Users.isAdmin(requestUserId) || ownerId==requestUserId){
			return new ValidatorStatusCode(true);
		}
		return new ValidatorStatusCode(false, "You do not have permission to view primitives owned by the given user");
	}
}
