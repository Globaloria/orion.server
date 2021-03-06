/*******************************************************************************
 * Copyright (c) 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.tests.metastore;

import static org.junit.Assert.assertEquals;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.orion.internal.server.core.metastore.SimpleMetaStore;
import org.eclipse.orion.server.core.OrionConfiguration;
import org.eclipse.orion.server.core.metastore.IMetaStore;
import org.eclipse.orion.server.core.metastore.UserInfo;
import org.eclipse.orion.server.core.metastore.WorkspaceInfo;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests to ensure that a workspace in a SimpleMetaStore can be successfully read and written concurrently from separate
 * threads. See Bug 462608
 *
 * @author Anthony Hunter
 */
public class SimpleMetaStoreWorkspacePropertyConcurrencyTests {

	protected static int THREAD_COUNT = 4;
	protected static int PROPERTY_COUNT = 4;

	protected JSONObject createProperty() throws JSONException {
		JSONObject property = new JSONObject();
		String propertyId = createRandomName();
		Date date = new Date();
		Format formatter = new SimpleDateFormat("EEEE MMMM d yyyy hh:mm:ss.SSS aaa");//$NON-NLS-1$
		property.put("timestamp", date.getTime());
		property.put("property", propertyId);
		property.put("description", "Created property " + propertyId + " at " + formatter.format(date));
		return property;
	}

	protected Thread createPropertyThread(int number) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					IMetaStore metaStore = OrionConfiguration.getMetaStore();
					String currentThreadName = Thread.currentThread().getName();
					for (int i = 0; i < PROPERTY_COUNT; i++) {
						// read the user
						UserInfo userInfo = metaStore.readUser("anthony");
						if (userInfo == null) {
							logger.debug("Meta File Error, could not read user anthony to add a property.");
							return;
						}

						// read the workspace
						String workspaceId = userInfo.getWorkspaceIds().get(0);
						WorkspaceInfo workspaceInfo = metaStore.readWorkspace(workspaceId);
						if (workspaceInfo == null) {
							logger.debug("Meta File Error, could not read workspace to add a property.");
							return;
						}

						// set a new property
						JSONObject propertyValue = createProperty();
						String propertyKey = "property/" + currentThreadName + "/" + propertyValue.getString("property");
						workspaceInfo.setProperty(propertyKey, propertyValue.toString());
						metaStore.updateWorkspace(workspaceInfo);

						// read the workspace again
						workspaceInfo = metaStore.readWorkspace(workspaceId);
						if (workspaceInfo == null) {
							logger.debug("Meta File Error, could not read workspace to verify the property.");
						} else if (workspaceInfo.getProperty(propertyKey) == null) {
							logger.debug("Meta File Error, JSONObject is missing " + propertyKey + " that was just added.");
						}
					}
				} catch (JSONException e) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.debug("Meta File Error, cannot read JSON file from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
				} catch (CoreException e) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.debug("Meta File Error, cannot read JSON file from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
			}
		};

		Thread thread = new Thread(runnable, "SimpleMetaStoreConcurrencyTestsThread-" + number);
		thread.start();
		return thread;
	}

	/**
	 * Create a random string of lower case letters between a length of eight and twelve characters to use as a unique
	 * name.
	 *
	 * @return a string of lower case letters.
	 */
	protected String createRandomName() {
		String characters = "abcdefghijklmnopqrstuvxwxyz";
		Random random = new Random();
		int length = 8 + random.nextInt(4);
		String name = new String();
		for (int i = 0; i < length; i++) {
			int next = random.nextInt(characters.length());
			name = name + characters.charAt(next);
		}
		return name;
	}

	protected Thread deletePropertyThread(int number) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				try {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					IMetaStore metaStore = OrionConfiguration.getMetaStore();
					String currentThreadName = Thread.currentThread().getName();
					List<String> propertyKeys = new ArrayList<String>();

					// read the user
					UserInfo userInfo = metaStore.readUser("anthony");
					if (userInfo == null) {
						logger.debug("Meta File Error, could not read user anthony to delete a property.");
						return;
					}

					// read the workspace
					String workspaceId = userInfo.getWorkspaceIds().get(0);
					WorkspaceInfo workspaceInfo = metaStore.readWorkspace(workspaceId);
					if (workspaceInfo == null) {
						logger.debug("Meta File Error, could not read workspace to delete a property.");
						return;
					}

					// get the list of properties to delete
					for (String key : workspaceInfo.getProperties().keySet()) {
						if (key.startsWith("property/" + currentThreadName)) {
							propertyKeys.add(key);
						}
					}

					// now delete the properties
					for (String key : propertyKeys) {
						// read the workspace
						workspaceInfo = metaStore.readWorkspace(workspaceId);
						if (workspaceInfo == null) {
							logger.debug("Meta File Error, could not read workspace to delete a property.");
							return;
						}

						// set a new property
						workspaceInfo.setProperty(key, null);
						metaStore.updateWorkspace(workspaceInfo);

						// read the workspace again
						workspaceInfo = metaStore.readWorkspace(workspaceId);
						if (workspaceInfo == null) {
							logger.debug("Meta File Error, could not read workspace to verify the property.");
						} else if (workspaceInfo.getProperty(key) != null) {
							logger.debug("Meta File Error, JSONObject contains " + key + " that was just deleted.");
						}
					}

				} catch (CoreException e) {
					Logger logger = LoggerFactory.getLogger("org.eclipse.orion.server.config"); //$NON-NLS-1$
					logger.debug("Meta File Error, cannot read JSON file from disk, reason: " + e.getLocalizedMessage()); //$NON-NLS-1$
				}
			}
		};

		Thread thread = new Thread(runnable, "SimpleMetaStoreConcurrencyTestsThread-" + number);
		thread.start();
		return thread;
	}

	/**
	 * Tests creating properties in the metadata store in multiple concurrently running threads.
	 *
	 * @throws CoreException
	 */
	@Test
	public void testSimpleMetaStoreCreateWorkspacePropertyConcurrency() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = OrionConfiguration.getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// add properties to the user in multiple threads
		Thread threads[] = new Thread[THREAD_COUNT];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = createPropertyThread(i);
		}

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// just continue
			}
		}

		// read the workspace and make sure the properties are there
		userInfo = metaStore.readUser("anthony");
		String workspaceId = userInfo.getWorkspaceIds().get(0);
		workspaceInfo = metaStore.readWorkspace(workspaceId);
		int count = 0;
		Map<String, String> properties = workspaceInfo.getProperties();
		for (String key : properties.keySet()) {
			if (key.startsWith("property/")) {
				count++;
			}
		}

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());

		assertEquals("Incomplete number of properties added for the user", THREAD_COUNT * PROPERTY_COUNT, count);
	}

	/**
	 * Tests deleting properties from the metadata store in multiple concurrently running threads.
	 *
	 * @throws CoreException
	 */
	@Test
	public void testSimpleMetaStoreDeleteWorkspacePropertyConcurrency() throws CoreException {
		// create the MetaStore
		IMetaStore metaStore = OrionConfiguration.getMetaStore();

		// create the user
		UserInfo userInfo = new UserInfo();
		userInfo.setUserName("anthony");
		userInfo.setFullName("Anthony Hunter");
		metaStore.createUser(userInfo);

		// create the workspace
		String workspaceName = SimpleMetaStore.DEFAULT_WORKSPACE_NAME;
		WorkspaceInfo workspaceInfo = new WorkspaceInfo();
		workspaceInfo.setFullName(workspaceName);
		workspaceInfo.setUserId(userInfo.getUniqueId());
		metaStore.createWorkspace(workspaceInfo);

		// add properties to the user in multiple threads
		Thread threads[] = new Thread[THREAD_COUNT];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = createPropertyThread(i);
		}

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// just continue
			}
		}

		// read the workspace and make sure the properties are there
		userInfo = metaStore.readUser("anthony");
		String workspaceId = userInfo.getWorkspaceIds().get(0);
		workspaceInfo = metaStore.readWorkspace(workspaceId);
		int count = 0;
		Map<String, String> properties = workspaceInfo.getProperties();
		for (String key : properties.keySet()) {
			if (key.startsWith("property/")) {
				count++;
			}
		}

		assertEquals("Incomplete number of properties added for the user", THREAD_COUNT * PROPERTY_COUNT, count);

		// delete properties in multiple threads
		threads = new Thread[THREAD_COUNT];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = deletePropertyThread(i);
		}

		for (Thread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// just continue
			}
		}

		// read the workspace and make sure the properties are there
		userInfo = metaStore.readUser("anthony");
		workspaceId = userInfo.getWorkspaceIds().get(0);
		workspaceInfo = metaStore.readWorkspace(workspaceId);
		count = 0;
		properties = workspaceInfo.getProperties();
		for (String key : properties.keySet()) {
			if (key.startsWith("property/")) {
				count++;
			}
		}

		// delete the user
		metaStore.deleteUser(userInfo.getUniqueId());

		assertEquals("Incomplete number of properties deleted for the user", 0, count);
	}

}
