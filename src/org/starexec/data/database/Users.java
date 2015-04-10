package org.starexec.data.database;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.starexec.constants.R;
import org.starexec.data.security.UserSecurity;
import org.starexec.data.to.DefaultSettings;
import org.starexec.data.to.DefaultSettings.SettingType;
import org.starexec.data.to.Space;
import org.starexec.data.to.User;
import org.starexec.util.Hash;

/**
 * Handles all database interaction for users
 */
public class Users {
	private static final Logger log = Logger.getLogger(Users.class);
		
	/**
	 * Associates a user with a space (i.e. adds the user to the space)
	 * @param con The connection to perform the database operation on
	 * @param userId The id of the user to add to the space
	 * @param spaceId The space to add the user to
	 * @param permId The permissions the user should have on the space
	 * @return True if the operation was a success, false otherwise
	 * @author Tyler Jensen
	 * @throws Exception 
	 */
	protected static boolean associate(Connection con, int userId, int spaceId) throws Exception {
		CallableStatement procedure= null;
		try {
			procedure = con.prepareCall("{CALL AddUserToSpace(?, ?)}");			
			procedure.setInt(1, userId);
			procedure.setInt(2, spaceId);
			

			procedure.executeUpdate();						
			log.info(String.format("User [%d] added to space [%d]", userId, spaceId));	
			return true;
		} catch (Exception e) {
			log.error("Users.associate says "+e.getMessage(),e);
		} finally {
			Common.safeClose(procedure);
		}
		return false;
	}
	
	/**
	 * Adds a new DefaultSettings object to the database. The type of the DefaultSettings
	 * object will be a USER settings object
	 * @param d The object to store in the database
	 * @return The new ID of the setting
	 */
	public static int createNewDefaultSettings(DefaultSettings d) {
		d.setType(SettingType.USER);
		return Settings.addNewSettingsProfile(d);
	}
	
	/**
	 * Gets the user preference for a default dataTables page size
	 * @param userId The ID of the user having the setting changed
	 * @param newSize the number of elements in a default table page
	 * @return
	 */
	public static boolean setDefaultPageSize(int userId,int newSize) {
		Connection con=null;
		CallableStatement procedure=null;
		
		try {
			con=Common.getConnection();
			procedure=con.prepareCall("{CALL SetDefaultPageSize(?,?)}");
			procedure.setInt(1, userId);
			procedure.setInt(2, newSize);
			procedure.executeUpdate();
			return true;
		} catch (Exception e){
			log.error(e.getMessage(),e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);


		}
		return false;
	}
	
	/**
	 * Gets the user preference for a default dataTables page size
	 * @param userId
	 * @return
	 */
	public static int getDefaultPageSize(int userId) {
		Connection con=null;
		CallableStatement procedure=null;
		ResultSet results = null;
		
		try {
			con=Common.getConnection();
			procedure=con.prepareCall("{CALL GetDefaultPageSize(?)}");
			procedure.setInt(1, userId);
			results=procedure.executeQuery();
			if (results.next()) {
				return results.getInt("pageSize");
				
			}
		} catch (Exception e){
			log.error(e.getMessage(),e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);

			Common.safeClose(results);

		}
		return 10;
	}
	
	/**
	 * Adds an association between a list of users and a space
	 * 
	 * @param con the database transaction to use
	 * @param userIds the ids of the users to add to a space
	 * @param spaceId the id of the space to add the users to
	 * @return true iff all users in userIds are successfully 
	 * added to the space represented by spaceId, false otherwise
	 * @throws Exception
	 * @author Todd Elvers
	 */
	protected static boolean associate(Connection con, List<Integer> userIds, int spaceId) throws Exception {
		for(int uid : userIds) {
			Users.associate(con, uid, spaceId);
		}
		return true;
	}
	
	/**
	 * Associates a user with a space (i.e. adds the user to the space)
	 * @param userId The id of the user to add to the space
	 * @param spaceId The space to add the user to
	 * @return True if the operation was a success, false otherwise
	 * @author Tyler Jensen
	 */
	public static boolean associate(int userId, int spaceId) {
		Connection con = null;			
		
		try {
			con = Common.getConnection();		
			Users.associate(con, userId, spaceId);
			
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
		}
		
		return false;
	}
	
	
	public static boolean associate(List<Integer> userIds, int spaceId, boolean hierarchy, int requestUserId) {
		if (!hierarchy) {
			return associate(userIds, spaceId);
		} else {
			List<Space> subspaces = Spaces.trimSubSpaces(requestUserId, Spaces.getSubSpaceHierarchy(spaceId, requestUserId));
			List<Integer> subspaceIds = new LinkedList<Integer>();
			
			// Add the destination space to the list of spaces to associate the user(s) with
			subspaceIds.add(spaceId);
			
			// Iterate once through all subspaces of the destination space to ensure the user has addUser permissions in each
			for(Space subspace : subspaces){	
				subspaceIds.add(subspace.getId());
			}
			return associate(userIds,subspaceIds);
		}
	}
	
	/**
	 * Associates a group of users user with a space (i.e. adds the user to the space)
	 * @param userIds The id's of the users to add to the space
	 * @param spaceId The space to add the users to
	 * @return True if the operation was a success, false otherwise
	 * @author Tyler Jensen
	 */
	public static boolean associate(List<Integer> userIds, int spaceId) {
		List<Integer> space=new ArrayList<Integer>();
		space.add(spaceId);
		return associate(userIds,space);
	}
	
	/**
	 * Adds an association between a list of users and a list of spaces, in an all-or-none fashion
	 * 
	 * @param userIds the ids of the users to add to the spaces
	 * @param spaceIds the ids of the spaces to add the users to
	 * @return true iff all spaces in spaceIds successfully have all 
	 * users in userIds add to them, false otherwise
	 * @author Todd Elvers
	 */
	public static boolean associate(List<Integer> userIds, List<Integer> spaceIds) {
		Connection con = null;			
		
		try {
			con = Common.getConnection();
			Common.beginTransaction(con);
			
			// For each space id in spaceIds, add all the users to it
			for(int spaceId : spaceIds) {
				Users.associate(con, userIds, spaceId);
			}
			
			log.info("Successfully added users " + userIds.toString() + " to spaces " + spaceIds.toString());
			Common.endTransaction(con);
			return true;
		} catch (Exception e){			
			log.error(e.getMessage(), e);
			Common.doRollback(con);
		} finally {
			Common.safeClose(con);
		}
		log.error("Failed to add users " + userIds.toString() + " to spaces " + spaceIds.toString());
		return false;
	}
	
	/**
	 * Given a ResultSet currently pointing at a row with a user in it, returns
	 * that user. 
	 * @param results
	 * @return
	 * @throws SQLException
	 */
	private static User resultSetToUser(ResultSet results) throws SQLException {
		User u = new User();
		u.setId(results.getInt("id"));
		u.setEmail(results.getString("email"));
		u.setFirstName(results.getString("first_name"));
		u.setLastName(results.getString("last_name"));
		u.setInstitution(results.getString("institution"));
		u.setCreateDate(results.getTimestamp("created"));
		u.setDiskQuota(results.getLong("disk_quota"));
		u.setSubscribedToReports(results.getBoolean("subscribed_to_reports"));
		u.setRole(results.getString("role"));
		return u;
	}
	
	/**
	 * Retrieves a user from the database given the user's id
	 * @param id the id of the user to get
	 * @return The user object associated with the user
	 * @author Tyler Jensen
	 */
	public static User get(int id){
		Connection con = null;			
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL GetUserById(?)}");
			procedure.setInt(1, id);					
			 results = procedure.executeQuery();
			
			if(results.next()){
				return resultSetToUser(results);
			} else {
				log.debug("Could not find user with id = "+id);
			}
			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		
		return null;
	}

	/**
	 * Retrieves a user from the database given the email address
	 * @param email The email of the user to retrieve
	 * @return The user object associated with the user
	 * @author Tyler Jensen
	 */
	public static User get(String email){
		Connection con = null;			
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL GetUserByEmail(?)}");
			procedure.setString(1, email);					
			results = procedure.executeQuery();
			
			if(results.next()){
				return resultSetToUser(results);

			}			
			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		
		return null;
	}

	/**
	 *
	 */
	public static List<User> getAllUsersSubscribedToReports() {
		return getUserListFromQuery("{CALL GetAllUsersSubscribedToReports()}");

	}

	public static List<User> getAdmins() {
		return getUserListFromQuery("{CALL GetAdmins()}");
	}

	/**
	 * NOTE: Make sure your your queries result-set has all the columns
	 * 		 that resultSetToUser checks for or it will throw an
	 * 		 exception. 
	 * Returns a list of users based on the sql database stored procedure that is
	 * input.
	 * @param sql An sql statement used for calling a database stored procedure.
	 * @return A List of users based on the sql procedure that was input.
	 * @author Albert Giegerich
	 */
	private static List<User> getUserListFromQuery(String sql) {
		Connection con = null;
		CallableStatement procedure = null;
		ResultSet results = null;
		try {
			con = Common.getConnection();
			procedure = con.prepareCall(sql);
			results = procedure.executeQuery();
			
			List<User> users =  new LinkedList<User>();
			while (results.next()) {
				User u = resultSetToUser(results);
				users.add(u);
			}
			return users;
				
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		return null;
	}
	
	
	/**
	 * Gets the number of Users in the whole system
	 * 
	 * @author Wyatt Kaiser
	 * @return The integer number of users in the system
	 */
	
	public static int getCount() {
		Connection con = null;
		CallableStatement procedure = null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			procedure = con.prepareCall("{CALL GetUserCount()}");
			results = procedure.executeQuery();
			
			if (results.next()) {
				return results.getInt("userCount");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}
		return 0;
	}
	
	/**
	 * Gets the number of Users in a given space
	 * 
	 * @param spaceId the id of the space to count the Users in
	 * @return the number of Users
	 * @author Todd Elvers
	 */
	public static int getCountInSpace(int spaceId) {
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserCountInSpace(?)}");
			procedure.setInt(1, spaceId);
			 results = procedure.executeQuery();

			if (results.next()) {
				return results.getInt("userCount");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}

		return 0;
	}
	
	/**
	 * Gets the number of Users in a given space that match a given query
	 * 
	 * @param spaceId the id of the space to count the Users in
	 * @param query The query to match the users against
	 * @return the number of Users
	 * @author Eric Burns
	 */
	public static int getCountInSpace(int spaceId, String query) {
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserCountInSpaceWithQuery(?, ?)}");
			procedure.setInt(1, spaceId);
			procedure.setString(2,query);
			 results = procedure.executeQuery();

			if (results.next()) {
				return results.getInt("userCount");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}

		return 0;
	}
	
	/**
	 * Gets the number of bytes a user is consuming on disk
	 * 
	 * @param userId the id of the user to get the disk usage of
	 * @return the disk usage of the given user
	 * @author Todd Elvers
	 */
	public static long getDiskUsage(int userId) {
		Connection con = null;
		long solverUsage=0;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserSolverDiskUsage(?)}");
			procedure.setInt(1, userId);

			results = procedure.executeQuery();
			while(results.next()){
				solverUsage=results.getLong("disk_usage");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		
		con = null;
		
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserBenchmarkDiskUsage(?)}");
			procedure.setInt(1, userId);

			 results = procedure.executeQuery();
			while(results.next()){
				return solverUsage+results.getLong("disk_usage");
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}

		return 0;
	}
	
	/**
	 * Returns the (hashed) password of the given user.
	 * @param userId the user ID of the user to get the password of
	 * @return The (hashed) password for the user
	 * @author Skylar Stark
	 */
	public static String getPassword(int userId) {
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetPasswordById(?)}");
			procedure.setInt(1, userId);
			 results = procedure.executeQuery();
			
			if (results.next()) {
				return results.getString("password");
			}
			
		} catch (Exception e) {			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		
		return null;
	}
	
	/**
	 * Retrieves an unregistered user from the database given their user_id.
	 * This is a helper method for user registration and shouldn't be used
	 * anywhere else.
	 * @param id The id of the unregistered user to retrieve
	 * @return the unregistered User object if one exists, null otherwise
	 * @author Todd Elvers
	 */
	public static User getUnregistered(int id) {
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUnregisteredUserById(?)}");
			procedure.setInt(1, id);
			 results = procedure.executeQuery();
			
			if (results.next()) {
				return resultSetToUser(results);
			}

		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}
		
		return null;
	}

	
	/**
	 * Returns true if a user is already registered with a given e-mail
	 * @param email - the email of the user to search for
	 * @return true if user exists; false if no user with that e-mail
	 * 
	 * @author Wyatt Kaiser
	 */

	public static boolean getUserByEmail(String email) {
		log.debug("email = " + email);
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserByEmail(?)}");
			procedure.setString(1,email);
			 results = procedure.executeQuery();
			
			if (results.next()) {
				return true;
			} else {
				return false;
			}
		} catch (Exception e) {
			log.error (e.getMessage(),e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		return false;
	}

	/**
	 * 
	 * @param jobId the job id to get the user for
	 * @return the user/owner of the job
	 * @author Wyatt Kaiser
	 */
	public static User getUserByJob(int jobId) {
		Connection con = null;
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			 procedure = con.prepareCall("{CALL GetUserByJob(?)}");
			procedure.setInt(1, jobId);
			 results = procedure.executeQuery();
			while (results.next()) {
				User u = resultSetToUser(results);
				return u;
			}
				
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		return null;
	}
	
	/**
	 * Gets the minimal number of Users necessary in order to service the client's
	 * request for the next page of Users in their DataTables object
	 * 
	 * @param startingRecord the record to start getting the next page of Users from
	 * @param recordsPerPage how many records to return (i.e. 10, 25, 50, or 100 records)
	 * @param isSortedASC whether or not the selected column is sorted in ascending or descending order 
	 * @param indexOfColumnSortedBy the index representing the column that the client has sorted on
	 * @param searchQuery the search query provided by the client (this is the empty string if no search query was inputed)
	 * @param spaceId the id of the space to get the Users from
	 * @return a list of 10, 25, 50, or 100 Users containing the minimal amount of data necessary
	 * @author Todd Elvers
	 */
	public static List<User> getUsersForNextPage(int startingRecord, int recordsPerPage, boolean isSortedASC, int indexOfColumnSortedBy,  String searchQuery, int spaceId) {
		Connection con = null;			
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			
			procedure = con.prepareCall("{CALL GetNextPageOfUsers(?, ?, ?, ?, ?, ?)}");
			procedure.setInt(1, startingRecord);
			procedure.setInt(2,	recordsPerPage);
			procedure.setInt(3, indexOfColumnSortedBy);
			procedure.setBoolean(4, isSortedASC);
			procedure.setInt(5, spaceId);
			procedure.setString(6, searchQuery);
			results = procedure.executeQuery();
			List<User> users = new LinkedList<User>();
			
			while(results.next()){
				User u = new User();
				u.setId(results.getInt("id"));
				u.setInstitution(results.getString("institution"));
				u.setFirstName(results.getString("first_name"));
				u.setLastName(results.getString("last_name"));
				u.setEmail(results.getString("email"));
				
				//Prevents public user from appearing in table.
				users.add(u);
				
							
			}	
			
			return users;
		} catch (Exception e){			
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}
		
		return null;
	}
	
	/**Gets the minimal number of Users necessary in order to service the client's 
	 * request for the next page of Users in their DataTables object
	 * 
	 * @param startingRecord the record to start getting the next page of Users from
	 * @param recordesPerpage how many records to return (i.e. 10, 25, 50, or 100 records)
	 * @param isSortedASC whether or not the selected column is sorted in ascending or descending order 
	 * @param indexOfColumnSortedBy the index representing the column that the client has sorted on
	 * @param searchQuery the search query provided by the client (this is the empty string if no search query was inputed)	 
	 * 
	 * @return a list of 10, 25, 50, or 100 Users containing the minimal amount of data necessary
	 * @author Wyatt Kaiser
	 **/
	public static List<User> getUsersForNextPageAdmin(int startingRecord, int recordsPerPage, boolean isSortedASC, int indexOfColumnSortedBy, String searchQuery) {
		Connection con = null;			
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();
			
			procedure = con.prepareCall("{CALL GetNextPageOfUsersAdmin(?, ?, ?, ?, ?)}");
			procedure.setInt(1, startingRecord);
			procedure.setInt(2,	recordsPerPage);
			procedure.setInt(3, indexOfColumnSortedBy);
			procedure.setBoolean(4, isSortedASC);
			procedure.setString(5, searchQuery);
			results = procedure.executeQuery();
			List<User> users = new LinkedList<User>();
			
			while(results.next()){
				User u = new User();
				u.setId(results.getInt("id"));
				u.setInstitution(results.getString("institution"));
				u.setFirstName(results.getString("first_name"));
				u.setLastName(results.getString("last_name"));
				u.setEmail(results.getString("email"));
				u.setRole(results.getString("role"));
				u.setSubscribedToReports(results.getBoolean("subscribed_to_reports"));

				//Prevents public user from appearing in table.
				users.add(u);
				
							
			}	
			
			return users;
		} catch (Exception e){			
			log.error(e.getMessage(), e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(results);
			Common.safeClose(procedure);
		}
		
		return null;
	}



	
	/**
	 * Checks to see whether the given user is in the given community
	 * @param userId The ID of the user in question
	 * @param communityId The ID of the community in question
	 * @return True if the user is in the community, false if not or on error
	 * @author Eric Burns
	 */
	
	public static boolean isMemberOfCommunity(int userId, int communityId) {
		Connection con=null;
		CallableStatement procedure=null;
		ResultSet results=null;
		try {
			con=Common.getConnection();
			procedure=con.prepareCall("{CALL IsMemberOfCommunity(?,?)}");
			procedure.setInt(1, userId);
			procedure.setInt(2,communityId);
			results=procedure.executeQuery();
			if (results.next()) {
				return results.getInt("spaceCount")>0;
			}
		} catch (Exception e) {
			log.error("isMemberOfCommunity says "+e.getMessage(),e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		return false;
	}
	
	

	/**
	 * Checks if a given user is a member of a particular space
	 * 
	 * @param userId the id of the user to check for membership in a particular space
	 * @param spaceId the id of the space to check for a given user's membership
	 * @return true iff the given user is a member of the given space, false otherwise
	 * @author Todd Elvers
	 */
	public static boolean isMemberOfSpace(int userId, int spaceId){
		Connection con = null;			
		CallableStatement procedure= null;
		ResultSet results=null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL IsMemberOfSpace(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setInt(2, spaceId);
			 results = procedure.executeQuery();
			return results.next();
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
			Common.safeClose(results);
		}
		
		return false;
	}
	
	/**
	 * Adds the specified user to the database. This method will hash the 
	 * user's password for them, so it must be supplied in plaintext.
	 * 
	 * @param user The user to add
	 * @param communityId the id of the community to add this user wants to join
	 * @param message the message from the user to the leaders of a community
	 * @param code the unique code to add to the database for this user
	 * @return True if the operation was a success, false otherwise
	 * @author Todd Elvers
	 */
	public static boolean register(User user, int communityId, String code, String message){
		log.debug("begin register..");
		Connection con = null;
		CallableStatement procedure= null;
		try{
			con = Common.getConnection();					
			Common.beginTransaction(con);
			
			String hashedPass = Hash.hashPassword(user.getPassword());
			
			 procedure = con.prepareCall("{CALL AddUser(?, ?, ?, ?, ?, ?, ?)}");
			procedure.setString(1, user.getFirstName());
			procedure.setString(2, user.getLastName());
			procedure.setString(3, user.getEmail());
			procedure.setString(4, user.getInstitution());
			procedure.setString(5, hashedPass);
			procedure.setLong(6, R.DEFAULT_USER_QUOTA);
			
			// Register output of ID the user is inserted under
			procedure.registerOutParameter(7, java.sql.Types.INTEGER);
			
			// Add user to the users table and check to be sure 1 row was modified
			procedure.executeUpdate();			
			
			// Extract id from OUT parameter
			user.setId(procedure.getInt(7));
			
			boolean successfulRegistration = false;
			
			// Add unique activation code to VERIFY table under new user's id
			if(Requests.addActivationCode(con, user, code)){
				// Add user's request to join a community to COMMUNITY_REQUESTS
				successfulRegistration = Requests.addCommunityRequest(con, user, communityId, code, message);
			}
			
			if(successfulRegistration){
				Common.endTransaction(con);
				log.info(String.format("New user [%s] successfully registered", user));
				return true;				
			} else {
				Common.doRollback(con);				
				log.info(String.format("New user [%s] failed to register", user));
				return false;
			}
		} catch (Exception e){	
			log.error(e.getMessage(), e);
			Common.doRollback(con);						
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}
	
	/**
	 * Sets a new disk quota for a given user (input should always be bytes)
	 * 
	 * @param userId the user to set the new disk quota for
	 * @param newDiskQuota the new disk quota, in bytes, to set for the given user
	 * @return true iff the new disk quota is successfully set, false otherwise
	 * @author Todd Elvers
	 */
	public static boolean setDiskQuota(int userId, long newDiskQuota) {
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL UpdateUserDiskQuota(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setLong(2, newDiskQuota);
			
			procedure.executeUpdate();	
			
			log.info(String.format("Disk quota changed to [%s] for user [%d]", FileUtils.byteCountToDisplaySize(newDiskQuota), userId));
			
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		
		log.warn(String.format("Failed to change disk quota to [%s] for user [%d]", FileUtils.byteCountToDisplaySize(newDiskQuota), userId));
		return false;
	}
	
	
	
	//We should not be using this right now, since our login setup can't handle changing email
	/**
	 * Updates the email address of a user in the database with the 
	 * given user ID
	 * @param userId the user ID of the user we want to update
	 * @param newValue what the email address will be updated to
	 * @return True if the operation was a success, false otherwise
	 * @author Skylar Stark
	 *
	public static boolean updateEmail(int userId, String newValue){
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL UpdateEmail(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setString(2, newValue);
			
			procedure.executeUpdate();			
			log.info(String.format("User [%d] updated e-mail address to [%s]", userId, newValue));
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}*/
	
	/**
	 * Updates the first name of a user in the database with the 
	 * given user ID
	 * 
	 * @param userId the user ID of the user we want to update
	 * @param newValue what the first name will be updated to
	 * @return True if the operation was a success, false otherwise
	 * @author Skylar Stark
	 */
	public static boolean updateFirstName(int userId, String newValue){
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			procedure = con.prepareCall("{CALL UpdateFirstName(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setString(2, newValue);
			
			procedure.executeUpdate();						
			log.info(String.format("User [%d] updated first name to [%s]", userId, newValue));
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}
	
	/**
	 * Updates the institution of a user in the database with the 
	 * given user ID
	 * @param userId the user ID of the user we want to update
	 * @param newValue what the institution will be updated to
	 * @return True if the operation was a success, false otherwise
	 * @author Skylar Stark
	 */
	public static boolean updateInstitution(int userId, String newValue){
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL UpdateInstitution(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setString(2, newValue);
			
			procedure.executeUpdate();			
			log.info(String.format("User [%d] updated institution to [%s]", userId, newValue));
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}
	
	/**
	 * Updates the last name of a user in the database with the 
	 * given user ID
	 * 
	 * @param userId the user ID of the user we want to update
	 * @param newValue what the last name will be updated to
	 * @return true iff the update succeeds on exactly one entry
	 * @author Skylar Stark
	 */
	public static boolean updateLastName(int userId, String newValue){
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL UpdateLastName(?, ?)}");
			procedure.setInt(1, userId);					
			procedure.setString(2, newValue);
			
			procedure.executeUpdate();			
			log.info(String.format("User [%d] updated last name to [%s]", userId, newValue));
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}
	
	/**
	 * Updates the password of a user in the database with the 
	 * given user ID. Hashes the password before updating, so
	 * the password should be supplied in plain-text.
	 * @param userId the user ID of the user we want to update
	 * @param newValue what the password will be updated to
	 * @return True if the operation was a success, false otherwise
	 * @author Skylar Stark
	 */
	public static boolean updatePassword(int userId, String newValue){
		Connection con = null;			
		CallableStatement procedure= null;
		try {
			con = Common.getConnection();		
			 procedure = con.prepareCall("{CALL UpdatePassword(?, ?)}");
			procedure.setInt(1, userId);
			String hashedPassword = Hash.hashPassword(newValue);
			procedure.setString(2, hashedPassword);
			
			procedure.executeUpdate();			
			log.info(String.format("User [%d] updated password", userId));
			return true;			
		} catch (Exception e){			
			log.error(e.getMessage(), e);		
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return false;
	}
	
	/**
	 * Completely deletes a user from the database. Right now, this is only
	 * being used to delete temporary users created during testing
	 * @param userIdToDelete The ID of the user to delete
	 * @param userIdMakingRequest The ID of the user trying to perform the deletion
	 * @return True on success, false on error
	 */
	public static boolean deleteUser(int userIdToDelete, int userIdMakingRequest) {
		Connection con=null;
		CallableStatement procedure=null;
		try {
			
			//Only allow the deletion of test users, and only if the admin is asking
			if (!UserSecurity.canDeleteUser(userIdToDelete, userIdMakingRequest).isSuccess()) {
				log.debug("security permission error when trying to delete user with id = "+userIdToDelete);
				return false;
			}
			if (!Users.isTestUser(userIdToDelete)) {
				log.debug("can't delete user with id = "+userIdToDelete+" because they are not a test user");
				return false; //we only want to delete test users for now
			}
			
			
			
			con=Common.getConnection();
			procedure=con.prepareCall("{CALL DeleteUser(?)}");
			procedure.setInt(1, userIdToDelete);
			procedure.executeQuery();
			return true;
		}catch (Exception e) {
			log.error("deleteUser says "+e.getMessage(),e);
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		log.debug("internal error trying to delete user with id = "+userIdToDelete);
		return false;
	}
	/**
	 * Checks to see whether the given user is an admin
	 * @param userId
	 * @return
	 */
	public static boolean isAdmin(int userId) {
		User u=Users.get(userId);
		return u!=null && u.getRole().equals(R.ADMIN_ROLE_NAME);
	}
	
	/**
	 * Checks to see whether the given user is the public user
	 * @param userId
	 * @return
	 */
	
	public static boolean isPublicUser(int userId) {
		return userId==R.PUBLIC_USER_ID;
	}
	
	
	/**
	 * Checks to see whether the given user is a test user
	 * @param userId
	 * @return
	 */
	public static boolean isTestUser(int userId) {
		User u=Users.get(userId);
		return u!=null && u.getRole().equals(R.TEST_ROLE_NAME);
	}
	
	/**
	 * Checks to see whether the given user is unauthorized
	 * @param userId
	 * @return
	 */
	public static boolean isUnauthorized(int userId) {
		User u=Users.get(userId);
		return u!=null && u.getRole().equals(R.UNAUTHORIZED_ROLE_NAME);
	}
	
	/**
	 * Checks to see whether the given user is suspended
	 * @param userId
	 * @return
	 */
	public static boolean isSuspended(int userId) {
		User u=Users.get(userId);
		return u!=null && u.getRole().equals(R.SUSPENDED_ROLE_NAME);
	}
	
	/**
	 * Checks to see whether the given user is a normal user
	 * @param userId
	 * @return
	 */
	public static boolean isNormalUser(int userId) {
		User u=Users.get(userId);
		return u!=null && u.getRole().equals(R.DEFAULT_USER_ROLE_NAME);
	}
	
	
	public static User getTestUser() {
		User u=Users.get(R.TEST_USER_ID);
		if (u==null) {
			log.warn("getTestUser could not find the test user. Please configure one");
		}
		return u;
	}
	
	public static int add(User user) {
		log.debug("beginning to add user...");
		log.debug("pass = " + user.getPassword());
		Connection con = null;
		CallableStatement procedure= null;
		try{
			con = Common.getConnection();					
			
			String hashedPass = Hash.hashPassword(user.getPassword());
			log.debug("hashedPass = " + hashedPass);
			procedure = con.prepareCall("{CALL AddUserAuthorized(?, ?, ?, ?, ?, ?, ?,?)}");
			procedure.setString(1, user.getFirstName());
			procedure.setString(2, user.getLastName());
			procedure.setString(3, user.getEmail());
			procedure.setString(4, user.getInstitution());
			procedure.setString(5, hashedPass);
			procedure.setLong(6, R.DEFAULT_USER_QUOTA);
			procedure.setString(7,user.getRole());

			// Register output of ID the user is inserted under
			procedure.registerOutParameter(8, java.sql.Types.INTEGER);
			
			// Add user to the users table and check to be sure 1 row was modified
			procedure.executeUpdate();						
			// Extract id from OUT parameter
			user.setId(procedure.getInt(8));
			log.debug("newid = " + user.getId());
			return user.getId();
		} catch (Exception e){	
			log.error(e.getMessage(), e);
			Common.doRollback(con);						
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}
		
		return -1;
	}

	
	/**
	 * Sets the role of the given user to the given role
	 * @param userId The ID of the user to affect
	 * @param role The role to give the user
	 * @return True on success and false otherwise
	 */
	public static boolean changeUserRole(int userId, String role) {
		Connection con = null;
		CallableStatement procedure= null;
		try{
			con = Common.getConnection();					
						
			procedure = con.prepareCall("{CALL ChangeUserRole(?,?)}");
			procedure.setInt(1, userId);
			procedure.setString(2, role);
			procedure.executeUpdate();			

			return true;
		} catch (Exception e){	
			log.error(e.getMessage(), e);
			Common.doRollback(con);						
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}		
		return false;
	}

	public static boolean setUserReportSubscription(int userId, Boolean willBeSubscribed) {
		Connection con = null;
		CallableStatement procedure= null;
		try{
			con = Common.getConnection();					
						
			procedure = con.prepareCall("{CALL SetUserReportSubscription(?,?)}");
			procedure.setInt(1, userId);
			procedure.setBoolean(2, willBeSubscribed);
			procedure.executeUpdate();			
			return true;
		} catch (Exception e){	
			log.error(e.getMessage(), e);
			Common.doRollback(con);						
		} finally {
			Common.safeClose(con);
			Common.safeClose(procedure);
		}		
		return false;
	}
	
	/**
	 * Sets the role of the given user to 'suspended' 
	 * NOTE: The old role of the user is not stored, so if they are later reinstated, they will always be 
	 * set to 'user,' regardless of their old role!
	 * @param userId
	 * @return
	 */
	public static boolean suspend(int userId) {
		return changeUserRole(userId,R.SUSPENDED_ROLE_NAME);
	}
	/**
	 * Sets the role of the given user back to 'user'
	 * @param userId
	 * @return
	 */
	public static boolean reinstate(int userId) {
		return changeUserRole(userId, R.DEFAULT_USER_ROLE_NAME);
	}	

	public static boolean subscribeToReports(int userId) {
		return setUserReportSubscription(userId, true); 
	}

	public static boolean unsubscribeFromReports(int userId) {
		return setUserReportSubscription(userId, false); 
	}
	
	/**
	 * Returns the list of community IDs for every community this user is a part of
	 * @param userId The ID of the user in question
	 * @return A list of community IDs for every community this user is a member of
	 * 
	 */
	public static List<Integer> getCommunities(int userId) {
		
		try {
			List<Integer> comms=new ArrayList<Integer>();
			for (Space s : Communities.getAll()) {
				if (Users.isMemberOfCommunity(userId, s.getId())) {
					comms.add(s.getId());
				}
			}
			
			return comms;
		} catch (Exception e) {
			log.error(e.getMessage(),e);
		} 
		return null;
	}
	
}
