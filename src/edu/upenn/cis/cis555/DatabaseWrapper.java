//package edu.upenn.cis.cis555;

/*TODO: 
 * 		- Adjust the key for page storage to remove protocol and domain info
 * 		- Store page meta instead as a secondary database with same key as page data 
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment; 
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockConflictException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

public class DatabaseWrapper {

	private Environment db_env; 
	private Database page_metadata, page_data, users, channels, passwords, xpaths, robots;
	private static final byte[] salt = "d*4g(2)9$_oiX7s%".getBytes(); 
	private static boolean channel_deleted = false, channel_added=false; 
	
	public DatabaseWrapper(String db_location) throws Exception
	{
		//Modify to directory name
		if(db_location.endsWith("/")) db_location.concat("/"); 
		
		//Setup Environment
		File file = new File(db_location); 
		file.mkdir(); 
		if(!file.exists() || !file.isDirectory())
		{		
			if(!file.mkdir())
				throw new IllegalArgumentException("Invalid directory name specified."); 
		}
	    EnvironmentConfig env_config = new EnvironmentConfig();
	    env_config.setAllowCreate(true);
	    db_env = new Environment(file, env_config);
	    setupDatabases(); 
	}
	
	void setupDatabases()
	{
		DatabaseConfig config = new DatabaseConfig(), config_dup = new DatabaseConfig();
		
		config.setAllowCreate(true); 
		config_dup.setAllowCreate(true); 
		config_dup.setSortedDuplicates(true);
		//Setup and configure Databases
		page_metadata = db_env.openDatabase(null, "page_metadata", config); 
		page_data = db_env.openDatabase(null, "page_data", config); 
	    passwords = db_env.openDatabase(null, "passwords", config); 
	    users = db_env.openDatabase(null, "users", config);
	    xpaths = db_env.openDatabase(null, "xpaths", config_dup); 
	    channels = db_env.openDatabase(null, "channels", config_dup);
	    robots = db_env.openDatabase(null, "robots", config);
	}
	
	//PAGE HANDLING METHODS
	
	/*
	 * Method used to store page metadata without page data or an association to 
	 * any channels. Replaces existing page metadata. 
	 * 
	 * @param page The Page object representing the metadata of the page
	 * 
	 * @return True on successful storage. 
	 */
	boolean addPageMetadata(Page page)
	{
		OperationStatus status = page_metadata.put(null, stringToDbEntry(page.url), pageToDbEntry(page)); 
		if(status.equals(OperationStatus.SUCCESS))
			return true;
		return false; 
	}
	
	/*
	 * Method used to store pages (both the metadata and raw data) that are to be 
	 * associated with channels. Replaces existing page/page metadata. 
	 * 
	 * @param page_meta The Page object representing the metadata of the page
	 * @param page The String representing the page data
	 * 
	 * @return True on successful storage. 
	 */
	boolean addPage(Page page_meta, String page)
	{
		if(page_meta!=null)
		{
			if(!addPageMetadata(page_meta)) return false; 
			OperationStatus status;
			return ((status = page_data.put(null, stringToDbEntry(page_meta.url), 
					stringToDbEntry(page))).equals(OperationStatus.SUCCESS));  
		}
		//A null page_meta indicates cold storage of key-value pair 
		//obtained by split on ws on String page
		String[] arr = page.split(" ",2); 
		page_data.put(null, stringToDbEntry(arr[0]), stringToDbEntry(arr[1]));
		return true; 
	}
	
	/*
	 * @param url 	The url of the page to be retrieved.
	 * 
	 * @return 		The requested Page object or null if it is not found. 
	 */
	Page retrievePageMetadata(String url)
	{
		DatabaseEntry data = new DatabaseEntry(); 
		OperationStatus status = page_metadata.get(null, stringToDbEntry(url), data, LockMode.DEFAULT);
		if(!status.equals(OperationStatus.SUCCESS)) 
			return null;
		return dbEntryToPage(data, url); 
	}
	
	/*
	 * @param url 	The url of the page to be retrieved.  
	 * 
	 * @return 		The requested page (as a String) or null if it is not found.
	 */
	String retrievePageData(String url)
	{
		DatabaseEntry data = new DatabaseEntry(); 
		OperationStatus status = page_data.get(null, stringToDbEntry(url), data, LockMode.DEFAULT);
		if(!status.equals(OperationStatus.SUCCESS)) 
			return null;
		return dbEntryToString(data);
	}
	
	boolean addPageToChannel(String page, String channel, String xpath)
	{
		Cursor cursor = channels.openCursor(null, null); 
		DatabaseEntry key = stringToDbEntry(channel), data = stringToDbEntry(page);
		OperationStatus status = cursor.getSearchKey(null, key, LockMode.DEFAULT); 
		if(status.equals(OperationStatus.NOTFOUND))
		{
			deleteXPathChannelPair(xpath, channel);
			return false; 
		}
		else
			status = cursor.putNoDupData(key, data); 
		
		if(!status.equals(OperationStatus.SUCCESS)) 
			return false;
		
		return true; 
	}
	
	/*
	 * Converts a Page to a DatabaseEntry for storage in page_metadata
	 */
	private DatabaseEntry pageToDbEntry(Page page)
	{
		StringBuffer buff = new StringBuffer(), buff2 = new StringBuffer(); 
		
		String[] arr = page.outgoing_urls.toArray(new String[page.outgoing_urls.size()]);
		for(String str : arr)
		{
			buff.append(str); 
			buff.append(" "); 
		}
		
		String[] arr2 = page.channels.toArray(new String[page.channels.size()]);
		for(String str : arr2)
		{
			buff2.append(str); 
			buff2.append(" "); 
		}
		

		String url = page.url, outgoing_urls = buff.toString(), channels = buff2.toString(), type = ((Integer)(page.type)).toString(),
		time_last_access = ((Long)page.time_last_access).toString(), crawl_delay = ((Long)page.crawl_delay).toString(),
		file_size = ((Long)page.file_size).toString(), can_crawl = page.can_crawl ? "1" : "0"; 
		
		//Section 1: outgoing urls
		buff = new StringBuffer(); 
		buff.append(outgoing_urls); 
		buff.append("<>"); 
		 
		//Section 2: page metadata (prepended with marker)
		buff.append("..");
		buff.append(url); 
		buff.append(" ");
		buff.append(type); 
		buff.append(" "); 
		buff.append(time_last_access); 
		buff.append(" "); 
		buff.append(crawl_delay); 
		buff.append(" ");
		buff.append(file_size);
		buff.append(" "); 
		buff.append(can_crawl); 
		buff.append("<>"); 
		
		//Section 3: page channels
		buff.append(channels);
		
		return stringToDbEntry(buff.toString()); 
	}
	
	/*
	 * Reconstructs a Page object from its corresponding DatabaseEntry in page_metadata
	 */
	private Page dbEntryToPage(DatabaseEntry entry, String url)
	{
		String[] arr = dbEntryToString(entry).split("<>", 3);
		Page page = new Page(url); 
		int x = 1, z = -1, y = -1; 
		
		//Case: no empty lists
		if(arr.length==3)
		{
			z=2; 
			y=0; 
			arr[1] = arr[1].substring(2);
		}
		else
		{
		
			//Case: one empty list
			if(arr.length==2)
			{
				if(arr[0].startsWith("\\.\\."))
				{
					x = 0; 
					z = 1; 
				}
				else
				{
					y=0; 
					x=1; 
				}
			}
			else
			{
				//Case: both lists empty
				if(arr.length==1)
					x = 0;
			}
		}
		
		//Obtain outgoing urls and channels
		if(y!=-1)
			page.outgoing_urls = new ArrayList<String>(Arrays.asList(arr[y].split(" ")));
		else
			page.outgoing_urls = new ArrayList<String>(); 
		if(z!=-1)
			page.channels = new ArrayList<String>(Arrays.asList(arr[z].split(" "))); 
		else
			page.channels = new ArrayList<String>(); 
		
		//Obtain metadata
		String[] arr2 = arr[x].split(" ");
		page.url = arr2[0]; 
		page.type = Integer.parseInt(arr2[1]); 
		page.time_last_access = Long.parseLong(arr2[2]); 
		page.crawl_delay = Long.parseLong(arr2[3]); 
		page.file_size = Long.parseLong(arr2[4]); 
		page.can_crawl = (arr2[5].equals("1")) ? true : false; 
		
		return page; 
	}
	
	//USER HANDLING METHODS
	
	/*
	 * @return Integer specifying status (refer to StatusCodes.java)
	 */
	int addUser(String user, String password)
	{
		DatabaseEntry key = stringToDbEntry(user), data = new DatabaseEntry(); 
		
		//Validity checks
		if(!user.matches("(\\p{Alnum}){5,16}"))
			return StatusCodes.USER_INVALID; 
		if(!password.matches("(\\p{Alnum}){6,16}"))
			return StatusCodes.PASSWORD_INVALID; 
		
		//Store new user
		OperationStatus status = null; 
		try {
			status = passwords.putNoOverwrite(null, key, new DatabaseEntry(sprinkle(password)));
		} catch (DatabaseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return StatusCodes.PASSWORD_INVALID; 
		} 
		if(status.equals(OperationStatus.KEYEXIST))
			return StatusCodes.USER_ALREADY_EXISTS; 
		
		return StatusCodes.SUCCESS; 
	}
	
	boolean validateUser(String user, String password)
	{
		OperationStatus status = null; 
		try {
			status = passwords.getSearchBoth(null, stringToDbEntry(user), 
					new DatabaseEntry(sprinkle(password)), LockMode.DEFAULT);
		} catch (LockConflictException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DatabaseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false; 
		} 
		if(status.equals(OperationStatus.SUCCESS)) return true; 
		return false; 
	}
	
	private byte[] sprinkle(String password) throws NoSuchAlgorithmException
	{	
		MessageDigest digest;
		byte[] hashed = null; 
		
		digest = MessageDigest.getInstance("SHA-256");
		digest.reset();
		digest.update(salt);
		try {
			hashed = digest.digest(password.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for (int i = 0; i < 1000; i++) {
			digest.reset();
			hashed = digest.digest(hashed);
		}
	
		return hashed;
	}
	
	//CHANNEL AND XPATH HANDLING METHODS
	
	/*
	 * Channel is created (if the name is not already reserved) and associated with 
	 * the given user.   
	 * 
	 * @param paths A String array of XPaths to be added to the store. 
	 * @param xsl The path to the XSL stylesheet for this channel. 
	 * @param channel The channel with which to associate the XPath. 
	 * @param user The user to associate a given channel with.  
	 * 
	 * @return Integer indicating status (refer to StatusCodes.java)
	 */
	int addChannel(String[] paths, String xsl, String channel, String user)
	{
		//Validate channel name
		if(!channel.matches("(\\S){1,20}"))
			return StatusCodes.INVALID_CHANNEL_NAME; 
		
		//Add channel info (top-level entry is XSL location followed by XPath entries) 
		//and ensure channel name is not reserved 
		OperationStatus status = null; 
		DatabaseEntry channel_dbe = stringToDbEntry(channel); 	
		 
		StringBuffer buff = new StringBuffer(); 
		buff.append("!"); 
		buff.append(xsl); 
		/*
		buff.append(" "); 
		for(String str : paths)
		{
			buff.append(str); 
			buff.append(" "); 
		}*/
		status = channels.putNoOverwrite(null, channel_dbe, stringToDbEntry(buff.toString()));
		if(status.equals(OperationStatus.KEYEXIST))
			return StatusCodes.ASSOCIATION_EXISTS;
		
		//Record that a new channel has been created
		if(!channel_added)
		{
			page_data.put(null, stringToDbEntry("!CHANNEL_ADD"), stringToDbEntry("1")); 
			channel_added = true; 
		}
			
		//Associate XPaths with channel
		for(String path : paths)
		{
			Cursor xpaths_cursor = xpaths.openCursor(null, null); 
			xpaths_cursor.putNoDupData(stringToDbEntry(path), channel_dbe); 
			xpaths_cursor.close(); 
		}
		
		//Associate new channel with user
		users.put(null, stringToDbEntry(user), channel_dbe);
		
		return StatusCodes.SUCCESS; 
	}
	
	boolean deleteChannel(String user, String channel)
	{
		DatabaseEntry key = stringToDbEntry(user), data = new DatabaseEntry(); 
		Cursor user_cursor = users.openCursor(null, null);  
		OperationStatus status = user_cursor.getSearchKey(key, data, LockMode.DEFAULT);
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			if(dbEntryToString(data).equals(channel))
			{
				key = stringToDbEntry(channel); 
				user_cursor.delete(); 
				/*channels.get(null, key, data, LockMode.DEFAULT);
				String[] arr = dbEntryToString(data).substring(1).split(" "); 
				for(int x=1;x<arr.length;x++)
					deleteXPathChannelPair(arr[x], channel); */
				channels.delete(null, key); 
				
				//Record that a channel has been deleted since
				//the previous crawl. Used to boost give a slight boost 
				//to crawling efficiency (in the event of no deletion). 
				if(!channel_deleted)
				{
					page_data.put(null, stringToDbEntry("!CHANNEL_DEL"), stringToDbEntry("1")); 
					channel_deleted = true; 
				}
				return true;
			}
			status = user_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		return false; 
	}
	
	/*
	 * Used to delete XPath/channel pairs that are invalid.
	 * 
	 * @param xpath 	The XPath (key) of the pair. 
	 * @param channel	The Channel (data) of the pair. 
	 * 
	 * @return True on success. 
	 */
	private boolean deleteXPathChannelPair(String xpath, String channel)
	{
		Cursor cursor = xpaths.openCursor(null, null); 
		OperationStatus status = cursor.getSearchBoth(stringToDbEntry(xpath), stringToDbEntry(channel), 
				LockMode.DEFAULT); 
		if(status.equals(OperationStatus.SUCCESS))
		{
			cursor.delete(); 
			return true; 
		}
		
		return false; 
	}
	
	/* Retrieves the XSL and URLs corresponding to a channel. s
	 * 
	 * @param channel The name of the channel whose data is to be retrieved. 
	 * 
	 * @return A String array. The first element is the url to the XSL stylesheet for
	 * the channel. All remaining entries are urls to pages matching the channel XPaths. 
	 * Returns empty array if channel does not exist.   
	 * 
	 */
	String[] retrieveChannelData(String channel)
	{
		StringBuffer buff = new StringBuffer(); 
		Cursor ch_cursor = channels.openCursor(null, null); 
		DatabaseEntry data = new DatabaseEntry(), key=stringToDbEntry(channel) ; 
		OperationStatus status = ch_cursor.getSearchKey(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(data)); 
			buff.append(" "); 
			status = ch_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		String[] output = buff.toString().split(" "); 
		
		for(int x=0;x<output.length;x++)
		{
			if(output[x].startsWith("!"))
			{
				output[x] = output[x].substring(1).split(" ")[0];	//gets xsl url
				
				if(x!=0)
				{
					String temp = output[x]; 
					output[x] = output[0]; 
					output[0] = temp; 
				}
				
				break; 
			}
		}
		
		return output; 
	}
	
	/*
	 * @return A string array containing the names of all available channels
	 */
	String[] retrieveAllChannelNames()
	{
		StringBuffer buff = new StringBuffer();
		Cursor channel_cursor = channels.openCursor(null, null); 
		DatabaseEntry key = new DatabaseEntry(), data = new DatabaseEntry(); 
		OperationStatus status = channel_cursor.getFirst(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(key)); 
			buff.append(" "); 
			status = channel_cursor.getNextNoDup(key, data, LockMode.DEFAULT); 
		}
		channel_cursor.close();
		return buff.toString().split(" ");
	}
	
	/*
	 * Used to retrieve all channels associated with a given user from the data store. 
	 * 
	 * @param user The name of the user whose channels are to be retrieved
	 * 
	 * @return A string array of channel names
	 */
	String[] retrieveUserChannelNames(String user)
	{
		StringBuffer buff = new StringBuffer(); 
		DatabaseEntry key = stringToDbEntry(user), data = new DatabaseEntry(); 
		Cursor user_cursor = users.openCursor(null, null); 
		OperationStatus status = user_cursor.getSearchKey(key, data, LockMode.DEFAULT);
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(data)); 
			buff.append(" ");
			status = user_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		user_cursor.close(); 
		return buff.toString().split(" "); 
	}
	
	/*
	 * @return All valid XPaths defined in the system
	 */
	HashMap<String, String[]> retrieveXPathMap()
	{
		HashMap<String, String[]> output = new HashMap<String, String[]>(); 
		StringBuffer buff; 
		Cursor xpaths_cursor = xpaths.openCursor(null, null); 
		DatabaseEntry key = new DatabaseEntry(), data = new DatabaseEntry(); 
		OperationStatus status = xpaths_cursor.getFirst(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			String temp_key = dbEntryToString(key);
			buff = new StringBuffer(); 
			while(status.equals(OperationStatus.SUCCESS) && temp_key.equals(dbEntryToString(key)))
			{
				buff.append(dbEntryToString(data)); 
				buff.append(" "); 
				status = xpaths_cursor.getNext(key, data, LockMode.DEFAULT); 
			}
			
			output.put(temp_key, buff.toString().split(" ")); 
		}
		
		xpaths_cursor.close();
		return output; 
	}
	
	/* DEPRECATED
	 * Used to retrieve all the channels associated with a given XPath
	 * 
	 * @param xpath The XPath whose channels are to be retrieved 
	 * 
	 * @return A String array containing channels associated with a given XPath
	 
	String[] retrieveXPathChannels(String xpath)
	{
		StringBuffer buff = new StringBuffer();
		xpaths_cursor = xpaths.openCursor(null, null); 
		DatabaseEntry key = stringToDbEntry(xpath), data = new DatabaseEntry(); 
		OperationStatus status = xpaths_cursor.getSearchKey(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(data)); 
			buff.append(" "); 
			status = xpaths_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		xpaths_cursor.close(); 
		return buff.toString().split(" ");
	}*/
	
	
	/* DEPRECATED
	 * Used to retrieve all XPaths associated with a given channel from the data store. 
	 * 
	 * @param channel The name of the channel whose xpaths are to be retrieved 
	 * 
	 * @return A String array containing the xpaths for the channel/user
	 
	String[] retrieveChannelXPaths(String channel)
	{
		String[] xpaths = null; 
		StringBuffer buffer = new StringBuffer(); 
		DatabaseEntry data = new DatabaseEntry(), key = stringToDbEntry(channel);  
		channel_cursor = channels.openCursor(null, null); 
		OperationStatus status = channel_cursor.getSearchKey(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buffer.append(dbEntryToString(data)); 
			buffer.append(" "); 
			status = channel_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		return buffer.toString().split(" "); 
	}*/
	
	//ROBOT HANDLING METHODS
	
	/*
	 * Adds new robot entry or replaces an existing one
	 */
	boolean insertRobot(Robot robot)
	{
		OperationStatus status = robots.put(null, stringToDbEntry(robot.host), robotToDbEntry(robot)); 
		if(status.equals(OperationStatus.SUCCESS))
			return true;
		return false;
	}
	
	HashMap<String, Robot> retrieveRobots()
	{
		HashMap<String, Robot> map = new HashMap<String, Robot>(); 
		Cursor robots_cursor = robots.openCursor(null, null); 
		DatabaseEntry key = new DatabaseEntry(), data = new DatabaseEntry(); 
		OperationStatus status = robots_cursor.getFirst(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			map.put(dbEntryToString(key), dbEntryToRobot(data));
			status = robots_cursor.getNext(key, data, LockMode.DEFAULT); 
		}
		
		robots_cursor.close(); 
		return map; 
	}
	
	private DatabaseEntry robotToDbEntry(Robot robot)
	{
		StringBuffer buff = new StringBuffer(); 
		
		String crawl_delay = ((Long)robot.crawl_delay).toString(),
		time_last_access = ((Long)robot.time_last_access).toString(), host = robot.host;
		
		buff.append(host); 
		buff.append(" "); 
		buff.append(time_last_access); 
		buff.append(" "); 
		buff.append(crawl_delay); 
		buff.append(" "); 
		if(robot.disallows!=null)
		{
			for(String str : robot.disallows)
			{
				buff.append(str); 
				buff.append(" "); 
			}
		}
		
		return stringToDbEntry(buff.toString()); 
	}
	
	private Robot dbEntryToRobot(DatabaseEntry entry)
	{
		String info = dbEntryToString(entry); 
		String[] arr = info.split(" "); 
		Robot robot = new Robot(arr[0]); 
		robot.time_last_access = Long.parseLong(arr[1]); 
		robot.crawl_delay = Long.parseLong(arr[2]); 
		if(arr.length>3)
			robot.disallows = new ArrayList<String>(Arrays.asList(arr).subList(3, arr.length)); 
		return robot; 
	}
	
	//GENERAL HELPER METHODS
	
	private DatabaseEntry stringToDbEntry(String str)
	{
		try {
			return new DatabaseEntry(str.getBytes("UTF-8"));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null; 
		} 
	}
	
	private String dbEntryToString(DatabaseEntry entry)
	{
		try {
			return new String(entry.getData(),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null; 
		}  
	}
	
	void close()
	{
		this.channels.close();
		this.page_metadata.close(); 
		this.page_data.close();
		this.passwords.close();
		this.xpaths.close();
		this.users.close(); 
		this.robots.close(); 
		db_env.close(); 
	}
}
