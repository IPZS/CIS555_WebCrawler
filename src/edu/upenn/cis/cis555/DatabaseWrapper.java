package edu.upenn.cis.cis555;

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
import java.net.URL;
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

	/*TODO:
	 *  - Confirm duplicates are supported as expected
	 */
	
	//IMPORTANT : The DatabaseWrapper class supports intentionally weak (essentially no) 
	//Exception handling. This forces top-level applications to utilize the API with appropriate inputs.  
	
	private Environment db_env; 
	private Database page_metadata, page_data, channels, passwords, xpaths, robots;
	public Database users; 
	private static final byte[] salt = "d*4g(2)9$_oiX7s%".getBytes(); 
	private static boolean channel_deleted = false, channel_added=false; 
	
	public DatabaseWrapper(String db_location) throws Exception
	{
		//Modify to directory name, if necessary
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
		
		//Supporting Duplicates (one-to-many)
	    users = db_env.openDatabase(null, "users", config_dup);
	    xpaths = db_env.openDatabase(null, "xpaths", config_dup); 
	    channels = db_env.openDatabase(null, "channels", config_dup);
	    
	    //Not Supporting Duplicates (one-to-one)
		page_metadata = db_env.openDatabase(null, "page_metadata", config); 
		page_data = db_env.openDatabase(null, "page_data", config); 
	    passwords = db_env.openDatabase(null, "passwords", config);
	    robots = db_env.openDatabase(null, "robots", config);
	}
	
	//PAGE HANDLING METHODS
	
	/*
	 * Stores page metadata (replaces existing page metadata). If
	 * data is intended to be stored with the page, use addPage(). 
	 * 
	 * @param page 	The Page object representing the metadata of the page.
	 * 
	 * @return 		True on successful storage. 
	 */
	boolean addPageMetadata(Page page) throws Exception 
	{
		OperationStatus status = page_metadata.put(null, 
				stringToDbEntry(page.url.getHost().concat(page.url.getPath()), null), pageToDbEntry(page)); 
		if(status.equals(OperationStatus.SUCCESS))
			return true;
		return false; 
	}
	
	/*
	 * Stores pages (both the metadata and raw data) that are to be 
	 * associated with channels. Replaces existing page/page metadata. 
	 * 
	 * @param page		The Page object representing the metadata of the page
	 * @param data 		The String representing the page data
	 * 
	 * @return True on successful storage. 
	 * 
	 */
	boolean addPage(Page page, String data) throws Exception 
	{
		if(page!=null)
		{
			//Add the metadata
			if(!addPageMetadata(page)) return false; 
			
			//Add the raw data
			OperationStatus status;
			return ((status = page_data.put(null, stringToDbEntry(page.url.getHost().concat(page.url.getPath()), null), 
					stringToDbEntry(data, page.encoding))).equals(OperationStatus.SUCCESS));  
		}
	
		return false; 
	}
	
	/*
	 * Retrieves requested metadata.
	 * 
	 * @param url 	The URL of the page to be retrieved.
	 * 
	 * @return 		The requested Page object or null if it is not found. 
	 */
	Page retrievePageMetadata(URL url) throws Exception 
	{
		DatabaseEntry data = new DatabaseEntry(); 
		OperationStatus status = page_metadata.get(null, 
				stringToDbEntry(url.getHost().concat(url.getPath()), null), 
				data, LockMode.DEFAULT);
		if(!status.equals(OperationStatus.SUCCESS)) 
			return null;
		return dbEntryToPage(data); 
	}
	
	/*
	 * @param page 	The Page object representing the data to be retrieved 
	 * 				(absolute requirement - use retrievePageMetaData with 
	 * 				the appropriate URL if necessary).   
	 * 
	 * @return 		The requested data or null if it is not found. 
	 */
	String retrievePageData(Page page) throws Exception 
	{
		DatabaseEntry data = new DatabaseEntry();
		OperationStatus status = page_data.get(null, 
				stringToDbEntry(page.url.getHost().concat(page.url.getPath()), null), data, LockMode.DEFAULT);
		if(!status.equals(OperationStatus.SUCCESS)) 
		{
			Logger.error("Database retrieval failure on key "+page.url.getHost().concat(page.url.getPath())
					+" "+status.toString()); 
			return null;
		}
		return dbEntryToString(data, page.encoding);
	}
	
	String retrieveDBParam(String param) throws Exception 
	{
		DatabaseEntry data = new DatabaseEntry(); 
		OperationStatus status = page_data.get(null, 
				stringToDbEntry(param, null), data, LockMode.DEFAULT);
		if(!status.equals(OperationStatus.SUCCESS)) 
			return null;
		return dbEntryToString(data, null);
	}
	
	boolean addPageToChannel(URL url, String channel, String xpath) throws Exception 
	{
		Cursor cursor = channels.openCursor(null, null); 
		DatabaseEntry key = stringToDbEntry(channel, null), 
		data = stringToDbEntry(url.toString(), null);
		
		//First attempt to retrieve the channel entry (and update if deleted)
		OperationStatus status = cursor.getSearchKey(key, new DatabaseEntry(), LockMode.DEFAULT); 
		if(status.equals(OperationStatus.NOTFOUND))
		{
			deleteXPathChannelPair(xpath, channel);
			return false; 
		}
		
		//Other error
		if(!status.equals(OperationStatus.SUCCESS))
			return false; 
		
		//Attempt to associate the page with the channel 
		status = cursor.put(key, data); 
		
		cursor.close(); 
		
		if(!status.equals(OperationStatus.SUCCESS)) 
			return false;
		
		return true; 
	}
	
	/*
	 * Converts a Page to a DatabaseEntry for storage in page_metadata
	 */
	private DatabaseEntry pageToDbEntry(Page page) throws Exception 
	{
		StringBuffer buff = new StringBuffer(), buff2 = new StringBuffer(); 
		
		for(String str : page.outgoing_urls)
		{
			buff.append(str); 
			buff.append(" "); 
		}
		
		for(String str : page.channels)
		{
			buff2.append(str); 
			buff2.append(" "); 
		}

		String 		url = page.url.toString(), 
					outgoing_urls = buff.toString(), 
					channels = buff2.toString(), 
					type = ((Integer)(page.type.ordinal())).toString(),
					time_last_access = ((Long)page.time_last_access).toString(), 
					crawl_delay = ((Long)page.crawl_delay).toString(),
					file_size = ((Long)page.file_size).toString(), 
					can_crawl = page.can_crawl ? "1" : "0",
					stored = page.stored ? "1" : "0",
					encoding = page.encoding; 
		
		buff = new StringBuffer(); 
		
		//Section 1
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
		buff.append(" "); 
		buff.append(stored);
		buff.append(" "); 
		buff.append(encoding);
		buff.append("<>"); 
		
		//Section 1: outgoing urls
		buff.append(outgoing_urls); 
		buff.append("<>");  
		
		//Section 3: page channels
		buff.append(channels);
		
		return stringToDbEntry(buff.toString(), null); 
	}
	
	/*
	 * Reconstructs a Page object from its corresponding DatabaseEntry in page_metadata
	 */
	private Page dbEntryToPage(DatabaseEntry entry) throws Exception 
	{
		ArrayList<String> outgoing_urls = new ArrayList<String>(), channels = new ArrayList<String>(); 
		String[] arr = dbEntryToString(entry, null).split("<>", 3);
		
		//Obtain outgoing urls and channels
		outgoing_urls.addAll(Arrays.asList(arr[1].split(" "))); 
		channels.addAll(Arrays.asList(arr[2].split(" "))); 
		
		//Reconstruct Page
		String[] arr2 = arr[0].split(" ");
		Page page = new Page(new URL(arr2[0]), null);  
		page.type = Status.Code.values()[(Integer.parseInt(arr2[1]))]; 
		page.time_last_access = Long.parseLong(arr2[2]); 
		page.crawl_delay = Long.parseLong(arr2[3]); 
		page.file_size = Long.parseLong(arr2[4]); 
		page.can_crawl = (arr2[5].equals("1")) ? true : false; 
		page.stored = (arr2[6].equals("1")) ? true : false;
		page.encoding = arr2[7]; 
		page.channels = channels; 
		page.outgoing_urls = outgoing_urls; 
		
		return page; 
	}
	
	//USER HANDLING METHODS
	
	/*
	 * @return Integer specifying status (refer to Status.java)
	 */
	Status.Code addUser(String user, String password) throws Exception 
	{
		DatabaseEntry key = stringToDbEntry(user, null), data = new DatabaseEntry(); 
		
		//Validity checks
		if(!user.matches("(\\p{Alnum}){5,16}"))
			return Status.Code.USER_INVALID; 
		if(!password.matches("(\\p{Alnum}){6,16}"))
			return Status.Code.PASSWORD_INVALID; 
		
		//Store new user
		OperationStatus status = null; 
		status = passwords.putNoOverwrite(null, key, new DatabaseEntry(sprinkle(password)));
		 
		if(status.equals(OperationStatus.KEYEXIST))
			return Status.Code.USER_ALREADY_EXISTS; 
		
		db_env.sync(); 
		
		return Status.Code.SUCCESS; 
	}
	
	boolean validateUser(String user, String password) throws Exception
	{
		OperationStatus status = null; 
		status = passwords.getSearchBoth(null, stringToDbEntry(user, null), 
					new DatabaseEntry(sprinkle(password)), LockMode.DEFAULT);
		 
		return status.equals(OperationStatus.SUCCESS);
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
	 * @return Integer indicating status (refer to Status.java)
	 */
	Status.Code addChannel(String[] paths, String xsl, String channel, String user) throws Exception
	{
		//Validate channel name
		if(!channel.matches("[\\p{Alnum}]{1,20}"))
			return Status.Code.INVALID_CHANNEL_NAME; 
		
		//Add channel info (top-level entry is XSL location followed by XPath entries) 
		//(and ensure channel name is not reserved)
		OperationStatus status = null; 
		DatabaseEntry channel_dbe = stringToDbEntry(channel, null); 	
		StringBuffer buff = new StringBuffer(); 
		//'!' marks entry with XSL URL
		buff.append("!"); 
		buff.append(xsl); 
		
		//Create new channel
		status = channels.putNoOverwrite(null, channel_dbe, stringToDbEntry(buff.toString(),null));
		if(status.equals(OperationStatus.KEYEXIST))
			return Status.Code.ASSOCIATION_EXISTS;
		
		//Record that a new channel has been created
		if(!channel_added)
		{
			page_data.put(null, stringToDbEntry("!CHANNEL_ADD",null), stringToDbEntry("1",null)); 
			channel_added = true; 
		}
			
		//Associate XPaths with channel
		for(String path : paths)
		{
			Cursor xpaths_cursor = xpaths.openCursor(null, null); 
			xpaths_cursor.put(stringToDbEntry(path, null), channel_dbe); 
			xpaths_cursor.close(); 
		}
		
		//Associate new channel with user
		users.put(null, stringToDbEntry(user,null), channel_dbe);
		
		//Sync 
		db_env.sync(); 
		
		return Status.Code.SUCCESS; 
	}
	
	boolean deleteChannel(String user, String channel) throws Exception 
	{
		DatabaseEntry key = stringToDbEntry(user,null), data = new DatabaseEntry(); 
		Cursor user_cursor = users.openCursor(null, null);  
		OperationStatus status = user_cursor.getSearchKey(key, data, LockMode.DEFAULT);
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			if(dbEntryToString(data,null).equals(channel))
			{
				key = stringToDbEntry(channel,null); 
				user_cursor.delete(); 
				channels.delete(null, key); 
				
				//Record that a channel has been deleted since
				//the previous crawl. Used to give a slight boost 
				//to crawling efficiency (in the event of no deletion). 
				if(!channel_deleted)
				{
					page_data.put(null, stringToDbEntry("!CHANNEL_DEL",null), stringToDbEntry("1",null)); 
					channel_deleted = true; 
				}
				
				user_cursor.close(); 
				db_env.sync(); 
				
				return true;
			}
			status = user_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		user_cursor.close(); 
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
	private boolean deleteXPathChannelPair(String xpath, String channel) throws Exception 
	{
		Cursor cursor = xpaths.openCursor(null, null); 
		OperationStatus status = cursor.getSearchBoth(stringToDbEntry(xpath, null), stringToDbEntry(channel, null), 
				LockMode.DEFAULT); 
		if(status.equals(OperationStatus.SUCCESS))
		{
			cursor.delete(); 
			cursor.close(); 
			return true; 
		}
		cursor.close(); 
		return false; 
	}
	
	/* Retrieves the XSL and URLs corresponding to a channel. s
	 * 
	 * @param channel The name of the channel whose data is to be retrieved. 
	 * 
	 * @return A String array. The first element is the URL to the XSL stylesheet for
	 * the channel. All remaining entries are URLs to Pages matching the channel XPaths. 
	 * Returns empty array if channel does not exist.   
	 * 
	 */
	String[] retrieveChannelData(String channel) throws Exception 
	{
		StringBuffer buff = new StringBuffer(); 
		Cursor ch_cursor = channels.openCursor(null, null); 
		DatabaseEntry data = new DatabaseEntry(), key=stringToDbEntry(channel, null) ; 
		OperationStatus status = ch_cursor.getSearchKey(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(data, null)); 
			buff.append(" "); 
			status = ch_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		String[] output = buff.toString().split(" "); 
		
		for(int x=0;x<output.length;x++)
		{
			if(output[x].startsWith("!h"))
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
		
		ch_cursor.close(); 
		return output; 
	}
	
	/*
	 * @return A string array containing the names of all available channels
	 */
	String[] retrieveAllChannelNames() throws Exception 
	{
		StringBuffer buff = new StringBuffer();
		Cursor channel_cursor = channels.openCursor(null, null); 
		DatabaseEntry key = new DatabaseEntry(), data = new DatabaseEntry(); 
		OperationStatus status = channel_cursor.getFirst(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(key,null)); 
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
		DatabaseEntry key = stringToDbEntry(user,null), next_key= new DatabaseEntry(), data = new DatabaseEntry();
		Cursor user_cursor = users.openCursor(null, null); 
		OperationStatus status = user_cursor.getSearchKey(key, data, LockMode.DEFAULT);
		next_key.setData(key.getData()); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buff.append(dbEntryToString(data,null));
			buff.append(" ");
			status = user_cursor.getNextDup(next_key, data, LockMode.DEFAULT); 
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
			String temp_key = dbEntryToString(key,null);
			buff = new StringBuffer(); 
			while(status.equals(OperationStatus.SUCCESS) && temp_key.equals(dbEntryToString(key,null)))
			{
				buff.append(dbEntryToString(data,null)); 
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
	boolean insertRobot(Robot robot) throws Exception 
	{
		OperationStatus status = robots.put(null, stringToDbEntry(robot.host, null), robotToDbEntry(robot)); 
		if(status.equals(OperationStatus.SUCCESS))
			return true;
		return false;
	}
	
	HashMap<String, Robot> retrieveRobots() throws Exception 
	{
		HashMap<String, Robot> map = new HashMap<String, Robot>(); 
		Cursor robots_cursor = robots.openCursor(null, null); 
		DatabaseEntry key = new DatabaseEntry(), data = new DatabaseEntry(); 
		OperationStatus status = robots_cursor.getFirst(key, data, LockMode.DEFAULT); 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			map.put(dbEntryToString(key, null), dbEntryToRobot(data));
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
		
		return stringToDbEntry(buff.toString(), null); 
	}
	
	private Robot dbEntryToRobot(DatabaseEntry entry) throws Exception 
	{
		String info = dbEntryToString(entry, null); 
		String[] arr = info.split(" "); 
		Robot robot = new Robot(arr[0]); 
		robot.time_last_access = Long.parseLong(arr[1]); 
		robot.crawl_delay = Long.parseLong(arr[2]); 
		if(arr.length>3)
			robot.disallows = new ArrayList<String>(Arrays.asList(arr).subList(3, arr.length)); 
		return robot; 
	}
	
	//GENERAL HELPER METHODS
	
	private DatabaseEntry stringToDbEntry(String str, String encoding)
	{
		byte[] b = null; 
		
		//Attempt to use the optionally provided encoding
		if(encoding!=null)
		{
			try {
				b = str.getBytes(encoding);
				return new DatabaseEntry(b); 
			}
			catch (Exception e)
			{
			}
		}
		
		//Default to UTF-8 (should be enforced system-wide)	
		try {
			b = str.getBytes("UTF-8");
			return new DatabaseEntry(b); 
		} catch (UnsupportedEncodingException e) {
			Logger.error("FATAL EXCEPTION: UTF-8 should have been recognized."); 
		} 
		
		//This should be unreachable
		return new DatabaseEntry(); 
	}
	
	private String dbEntryToString(DatabaseEntry entry, String encoding)
	{
		byte[] b = entry.getData(); 
		
		//Attempt to decode using the scheme specified
		if(encoding!=null)
		{
			try {
				return new String(b, encoding);
			} catch (UnsupportedEncodingException e) {
			} 
		}
		
		//Default to decode using UTF-8 
		//NOTE: If this is data, it may be lost. Ideally, the encoding should 
		//have been stored in the page's meta.
		try {
			return new String(b, "UTF-8"); 
		} catch (UnsupportedEncodingException e) { 
			Logger.error("FATAL EXCEPTION: UTF-8 should have been recognized.");
		}  
		
		//This should be unreachable
		return new String(); 
	}
	
	void sync()
	{
		try
		{
			db_env.sync(); 
		}
		catch(Exception e)
		{
			Logger.error("Synchronization of database failed. StackTrace follows."); 
			e.printStackTrace(Logger.getErrorWriter()); 
		}
	}
	
	void close() throws Exception 
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
