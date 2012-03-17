package edu.upenn.cis.cis555;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment; 
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

public class DatabaseWrapper {

	private Environment db_env; 
	private Database pages, users, xsl_sheets, channels, robots; 
	private Cursor channel_cursor; 
	
	public DatabaseWrapper(String db_location) throws Exception
	{
		//Setup Environment
		File file = new File(db_location); 
	    EnvironmentConfig env_config = new EnvironmentConfig();
	    env_config.setAllowCreate(true);
	    db_env = new Environment(file, env_config);
	    
	}
	
	void setupDatabases()
	{
		//Setup and configure Databases
	    pages = db_env.openDatabase(null, "pages", null); 
	    users = db_env.openDatabase(null, "users", null);
	    xsl_sheets = db_env.openDatabase(null, "xsl_sheets", null);  
	    channels = db_env.openDatabase(null, "channels", null); 
	    channels.getConfig().setSortedDuplicates(true); 
	    robots = db_env.openDatabase(null, "robots", null);
	    
	    //Setup Cursors
	    channel_cursor = channels.openCursor(null, null); 
	}
	
	/*
	 * Associates an XPath expression with a channel and places it in the store.
	 * Channel is created if it doesn't already exist.  
	 * 
	 * @param path The XPath to be added to the store. 
	 * @param channel The channel with which to associate the XPath. 
	 * 
	 * @return True if the add was successful. False otherwise. 
	 */
	boolean addXPath(String path, String channel)
	{
		OperationStatus status = OperationStatus.NOTFOUND; 
		
		try{
			status = channels.put(null, new DatabaseEntry(channel.getBytes()), 
				new DatabaseEntry(path.getBytes())); 
		}
		catch (Exception e)
		{
			System.out.println("Error adding XPath "+path+" to channel "+channel); 
			return false; 
		}
		
		return status.equals(OperationStatus.SUCCESS); 
	}
	
	/*
	 * Used to retrieve the all XPaths associated with a given channel from the data store. 
	 * 
	 * @param channel The name of the channel whose xpaths are to be retrieved 
	 * 
	 * @return A String array containing the xpaths for the channel
	 */
	String[] retrieveXPaths(String channel)
	{
		String[] xpaths = null; 
		StringBuffer buffer = new StringBuffer(); 
		DatabaseEntry data = null, key = new DatabaseEntry(channel.getBytes());  
		OperationStatus status = null;  
		
		status = channel_cursor.getFirst(key, data, LockMode.DEFAULT); 
		if(status.equals(OperationStatus.NOTFOUND)) return null; 
		
		while(status.equals(OperationStatus.SUCCESS))
		{
			buffer.append(new String(data.getData())); 
			buffer.append(";"); 
			status = channel_cursor.getNextDup(key, data, LockMode.DEFAULT); 
		}
		
		return buffer.toString().split(";"); 
	}
	
	void addRobotDisallows(String server, String resource)
	{
		DatabaseEntry key = new DatabaseEntry(server.getBytes()); 
		DatabaseEntry data = new DatabaseEntry(resource.getBytes()); 
		HashMap<String, String> map = new HashMap<String, String>();  
		robots.put(null, key, data); 
	}
}
