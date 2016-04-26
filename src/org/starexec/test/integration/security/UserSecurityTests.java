package org.starexec.test.integration.security;

import org.junit.Assert;
import org.starexec.constants.R;
import org.starexec.data.database.Users;
import org.starexec.data.security.UserSecurity;
import org.starexec.data.to.User;
import org.starexec.test.TestUtil;
import org.starexec.test.integration.StarexecTest;
import org.starexec.test.integration.TestSequence;
import org.starexec.test.resources.ResourceLoader;

public class UserSecurityTests extends TestSequence {

	User user1=null;
	User user2=null;
	User admin=null;
	
	@StarexecTest
	private void canUpdateFirstNameTest() {
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), user1.getId(), "firstname","test").isSuccess());
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), admin.getId(), "firstname","test").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(admin.getId(), user1.getId(), "firstname","test").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(user1.getId(), user2.getId(), "firstname","test").isSuccess());
	}
	
	@StarexecTest
	private void CanDeleteUserTest() {
		Assert.assertTrue(UserSecurity.canDeleteUser(user1.getId(), admin.getId()).isSuccess());
		Assert.assertTrue(UserSecurity.canDeleteUser(user2.getId(), admin.getId()).isSuccess());

		Assert.assertFalse(UserSecurity.canDeleteUser(user1.getId(), user1.getId()).isSuccess());

		Assert.assertFalse(UserSecurity.canDeleteUser(admin.getId(), user1.getId()).isSuccess());

		Assert.assertFalse(UserSecurity.canDeleteUser(admin.getId(), admin.getId()).isSuccess());
		Assert.assertFalse(UserSecurity.canDeleteUser(user1.getId(), user2.getId()).isSuccess());
	}

	@StarexecTest
	private void canUpdateLastNameTest() {
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), user1.getId(), "lastname","test").isSuccess());
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), admin.getId(), "lastname","test").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(admin.getId(), user1.getId(), "lastname","test").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(user1.getId(), user2.getId(), "lastname","test").isSuccess());
	}
	
	@StarexecTest
	private void canUpdateInstitutionTest() {
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), user1.getId(), "institution","Iowa").isSuccess());
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), admin.getId(), "institution","Iowa").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(admin.getId(), user1.getId(), "institution","Iowa").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(user1.getId(), user2.getId(), "institution","Iowa").isSuccess());
	}

	
	@StarexecTest
	private void canUpdateDiskQuotaTest() {
		//only admins can update anyone's disk quota
		Assert.assertEquals(true, UserSecurity.canUpdateData(user1.getId(), admin.getId(), "diskquota","10").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(admin.getId(), user1.getId(), "diskquota","10").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(user1.getId(), user2.getId(), "diskquota","10").isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canUpdateData(user1.getId(), user1.getId(), "diskquota","10").isSuccess());
	}
	
	@StarexecTest
	private void canViewUserPrimitives() {
		Assert.assertEquals(true, UserSecurity.canViewUserPrimitives(user1.getId(),user1.getId()).isSuccess());
		Assert.assertEquals(true, UserSecurity.canViewUserPrimitives(user1.getId(),admin.getId()).isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canViewUserPrimitives(admin.getId(),user2.getId()).isSuccess());
		Assert.assertNotEquals(true, UserSecurity.canViewUserPrimitives(user1.getId(),user2.getId()).isSuccess());
	}

	@Override
	protected String getTestName() {
		return "UserSecurityTests";
	}

	@Override
	protected void setup() throws Exception {
		user1=loader.loadUserIntoDatabase();
		user2=loader.loadUserIntoDatabase();

		admin=loader.loadUserIntoDatabase(TestUtil.getRandomAlphaString(10),TestUtil.getRandomAlphaString(10),TestUtil.getRandomPassword(),TestUtil.getRandomPassword(),"The University of Iowa",R.ADMIN_ROLE_NAME);
		Assert.assertNotNull(user1);
		Assert.assertNotNull(user2);
		Assert.assertNotNull(admin);
	}

	@Override
	protected void teardown() throws Exception {
		loader.deleteAllPrimitives();
	}

}
