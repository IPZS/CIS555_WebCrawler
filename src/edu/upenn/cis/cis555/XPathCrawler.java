import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;

//package edu.upenn.cis.cis555;

/*TODO:
 * 
 * 	ISSUES:
 */

public class XPathCrawler {
	
	private static URL start_url; 
	private static DatabaseWrapper db;
	//nested mapping to check previously crawled pages (consider alternatives for future 
	//versions as this can become too large to cache in a broader application)
	private static HashMap<String, HashMap<String, HashSet<String>>> crawled; 
	//mapping of hosts to robot profiles 
	private static HashMap<String, Robot> robots; 
	//robot updating trackers
	private static HashSet<String> checked_robots, modified_robots;
	//channel listing (cloned per call to optimize on updateChannels; this is a necessary inefficiency)  
	//Additional Information: 
	//Design tradeoff that results from the constraint of having to check every single XPath of every channel against 
	//a document. This is a nonsensical requirement of the project specs since a single match to any XPath in a channel 
	//is all the info we actually need to associate it with a channel - if we took advantage of this, we could 
	//eliminate the need to run a possibly substantial number of XPath checks. The decision here ultimately depends
	//on what's required by the application (e.g. a certain 'hit rate' on XPaths). 
	private static HashSet<String> channels; 
	//Stores a (one-to-many) mapping of xpaths to channels (note: actual relationship can be many-to-many)
	private static HashMap<String, String[]> xpath_map;
	//file size limit, in bytes
	private static long MAX_FILE_SIZE;	
	//file download limit, default to infinite
	private static long max_files_retrieved = -1; 		
	//the URL frontier of Pages, expanded as the crawler progresses in BFS fashion
	private static LinkedList<URL> frontier;
	//booleans indicating channel modification between crawls (allowing for savings in XPath checking)
	//if no channels were added, then stored pages that have not been modified need not be rechecked
	//if no channels were deleted, then there is no need to revise page-channel(s) associations
	private static boolean channel_deleted = true, channel_added = true; 
	
	private static void runCrawler()
	{
		//Store the XPath to channel mapping
		xpath_map = db.retrieveXPathMap(); 
		if(xpath_map.isEmpty())
		{
			Logger.log("No XPaths stored."); 
			return; 
		}
		
		//Retrieve stored robots
		robots = db.retrieveRobots(); 
		
		//Initialize robot sets
		checked_robots = new HashSet<String>(); 
		modified_robots = new HashSet<String>(); 
		
		//Store URLs already crawled
		crawled = new HashMap<String, HashMap<String, HashSet<String>>>(); 
		
		//Store channel set 
		channels = new HashSet<String>(); 
		channels.addAll(Arrays.asList(db.retrieveAllChannelNames())); 
		
		//Check if a channel was deleted since last crawl
		String ch_check = db.retrievePageData("!CHANNEL_DEL");
		if(ch_check!=null)
		{
			channel_deleted = ch_check.equals("1") ? true : false; 
			if(channel_deleted) db.addPage(null, "!CHANNEL_DEL 0"); 
		}
		
		//ch_check if a channel was added since last crawl
		ch_check = db.retrievePageData("!CHANNEL_ADD");
		if(ch_check!=null)
		{
			channel_added= ch_check.equals("1") ? true : false; 
			if(channel_added) db.addPage(null, "!CHANNEL_ADD 0"); 
		}
		
		//Initialize URL Frontier (BFS progression)
		frontier = new LinkedList<URL>(); 
		frontier.add(start_url); 
		
		//Main execution loop 
		//Handles each page encountered until completion
		while(max_files_retrieved!=0 && !frontier.isEmpty())
		{
			boolean status = crawl(frontier.remove()); 
			Logger.log(status ? "" : "Crawl terminated prematurely.\n");
			System.out.print("."); 
		}
	}
	
	private static boolean crawl(URL url)
	{ 
		long start=System.currentTimeMillis(), th=0, td=0, ts=0;
		boolean is_new = false; 
		
		//Timer 
		String url_key = url.toString(); 
		Logger.log("Checking URL : "+url_key); 
		start = System.currentTimeMillis(); 

		//crawled ? return 
		if(crawledThisRun(url))
		{
			Logger.log("Previously crawled this iteration."); 
			return false;  
		}
		
		Page page = null;
		
		//Retrieve Page from store or create new one if not previously crawled
		//TODO: Pages should store URL objects directly (for the sake of sanity)
		if((page = db.retrievePageMetadata(url_key))==null)
		{
			page = new Page(url_key); 
			is_new = true; 
		}
		
		//Update robots.txt info and page restrictions
		isDisallowed(page, is_new); 
		
		//Skip page if not crawlable 
		if(!page.can_crawl)
		{
			markAsCrawled(url); //to avoid rechecking and traps
			Logger.log("Page crawl is disallowed."); 
			return false; 
		}
			
		//Check the page delay 
		//Design Decision: only one delay per pair of requests since HEAD is low overhead
		if(page.crawl_delay!=-1)
		{
			//if crawl delay hasn't expired, place on the back of the queue
			if(((new Date()).getTime() - page.time_last_access)<(page.crawl_delay*1000)) 
			{
				Logger.log("Remaining time before next crawl: "+
						(-1*(((new Date()).getTime() - page.time_last_access)-(page.crawl_delay*1000))));
				db.addPageMetadata(page); 
				frontier.add(url);
				return false; 
			}
		}
		
		//If not delaying crawl, mark as crawled
		markAsCrawled(url);
		
		//Condition Handling

		boolean is_modified = true; 			
		//Perform appropriate HEAD check and retrieve headers
		
		//If previously stored, check for modification
		/*ArrayList<String> headers = null;
		if(!is_new)
		{
			headers = new ArrayList<String>(1); 
			try {
				headers.add("If-Modified-Since: "+URLMessenging.dateToStringURL(new Date(page.time_last_access)));
				Logger.log(headers.get(0)); 
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} 
		}*/
		
		//Obtain HEAD response 
		HashMap<String, String> response = null;
		try {
			th = System.currentTimeMillis(); 
			response = URLMessenging.checkHead(url_key, null);
			Logger.log("HEAD Request Time: "+(th=System.currentTimeMillis()-th)+"ms"); 
		} catch (Exception e2) {
			// Any exception thrown indicates an invalid connection 
			e2.printStackTrace();
			Logger.log("Unable to establish a connection."); 
			return false; 
		} 
		
		//Invalid URL
		if(response==null)
		{
			Logger.log("Unable to establish a connection.");
			return false; 
		}
		
		String header; 
		
		//Check validity 
		if((header=response.get("initial"))!=null)
		{
			if(!header.contains("200"))
			{
				if(header.contains("301") || header.contains("307"))
				{
					if((header=response.get("location"))!=null)
					{
						try {
							//add to the beginning of the frontier to maintain 
							//expansion order 
							frontier.addFirst(new URL(header.trim()));
							Logger.log("Redirecting."); 
							return false; 
						} catch (MalformedURLException e) {
							// TODO Auto-generated catch block
							Logger.error(e.toString());
						} 
					}
				}
				else
					Logger.log("Received (Invalid) Status Code\nStatus Header: "+header); 
				return false; 
			}
		}
		
		long last_modified = 0; 
		//Check last_modified 
		if((header=response.get("last-modified"))!=null)
		{
			try {
				last_modified = URLMessenging.extractDateURL(header.trim()).getTime();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} 
		}
		is_modified = last_modified>page.time_last_access || is_new; 
		
		//If no channel has been added and the page is not modified, return
		if(!is_modified && !channel_added)
		{
			Logger.log("No channel has been added and the page is unmodified."); 
			return false;
		}
		
		if(is_modified)
			Logger.log("Page is either new or has been modified."); 
		
		//Update page meta on creation (indicated by is_modified, which 
		//defaults to true in this case) or modification.
		if(is_modified)
		{
			//Update content type 
			if((header=response.get("content-type"))!=null)
			{
				if(header.contains("text/xml") || header.contains("application/xml"))
					page.type=StatusCodes.XML; 
				else
				{
					if(header.contains("text/html"))
						page.type=StatusCodes.HTML; 
					else
					{
						if(url.getPath().endsWith(".rss") || header.contains("application/rss+xml"))
							page.type = StatusCodes.RSS;
						else
							page.type=StatusCodes.OTHER_TYPE; 
					}
				}
			}
			//If header absent, attempt to isolate page type from path extension
			else
			{
				if(url.getPath().endsWith(".html"))
					page.type = StatusCodes.HTML; 
				if(url.getPath().endsWith(".xml"))
					page.type = StatusCodes.XML;
				if(url.getPath().endsWith(".rss"))
					page.type = StatusCodes.RSS;
			}
		
			//Update file size
			if((header=response.get("content-length"))!=null)
			{
				long length = -1; 
				try
				{
					length = Long.parseLong(header.trim()); 
					if(length>-1)
						page.file_size = length; 
				}
				catch(Exception e)
				{
					Logger.error(e.toString()); 
				}
			}
		}
		
		//Ignore page if not HTML, RSS, or XML (or if 
		//unable to definitively ascertain content type)
		if(page.type==StatusCodes.OTHER_TYPE)
		{
			Logger.log("File is an invalid type."); 
			return false;
		}

		//Ignore page if too large
		if(page.file_size>MAX_FILE_SIZE)
		{
			Logger.log("File size is in excess of limit."); 
			return false;  
		}
		
		String data = null; 

		//Parse links and acquire page data via URL if new or if modified.
		if(is_modified)
		{
			td = System.currentTimeMillis(); 
			data = updatePageAndData(page); 
			Logger.log("Downloading page from URL. Total Time: "+(td=System.currentTimeMillis()-td)+"ms"); 
			if(data==null)
				Logger.log("ERROR: unable to retrieve data");
			else
				max_files_retrieved--; //decrement on successful file download
		}
		
		//With stored page meta and data now updated, can approximate time of last access.
		page.time_last_access = (new Date().getTime()); 
		
		boolean matched = false; 
		
		//Update channels as appropriate
		matched = updateChannels(data, page, is_modified);
		
		//Page is finalized. Update db as appropriate.
		//NOTE: remove "&& matched" to generate a broader crawler (store all encountered)
		if(data!=null) 
		{
			ts = System.currentTimeMillis();
			db.addPage(page, data); 
			Logger.log("Storing to disk. Total Time: "+(ts=System.currentTimeMillis()-ts)+"ms"); 
		}
		else
		{
			//Design Decision: Always store meta for a page (no need to parse outgoing links on subsequent calls)
			if(is_modified) 
			{
				Logger.log("Updated metadata"); 
				db.addPageMetadata(page); 	
			}
		}
		
		//Expand frontier with any outgoing_urls
		//Logger.log("Outgoing Links: "); 
		for(String link : page.outgoing_urls)
		{
			URL next_url = null; 
			try
			{
				next_url = new URL(link);
			}
			catch(MalformedURLException e)
			{
				try{
					next_url = new URL(url.getProtocol(),url.getHost(), link); 
				}
				catch(MalformedURLException e1)
				{
					Logger.error(e1.toString()); 
					Logger.log("unrecognizable url format"); 
				}
			}
			if(next_url!=null)
			{
				frontier.add(next_url); 
				//Logger.log(next_url.toString()+", "); 
			}
		}
	
		Logger.log("Total Elapsed Time: "+(start=System.currentTimeMillis()-start)+"ms");
		Logger.log("Percentage Time Due to Local Overhead: "+(100.00-((double)(td+th)/(double)start)*100.00)+"%"); 
		//Logger.log("Percentage Time Due to Application: "+(100.00-((double)(td+th+ts)/(double)start)*100.00)+"%"); 
		
		return true; 
	}
	
	private static String updatePageAndData(Page page)
	{
		//Retrieve the page data and, optionally, outgoing links
		ArrayList<String> parsed_list = null;
		try {
			parsed_list = URLMessenging.outputToString(page.url, page.type==StatusCodes.HTML, null);
		} catch (Exception e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}
		
		URL url = null;
		try {
			url = new URL(page.url);
		} catch (MalformedURLException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		} 
		
		//error state
		if(parsed_list==null || parsed_list.isEmpty())
			return null; 
		
		String data = parsed_list.get(0); 
		
		boolean found_invalid = false; 
		//Check for invalid URLs
		if(parsed_list.size()>1)
		{
			for(int x=1;x<parsed_list.size();x++)
			{
				String link = parsed_list.get(x); 
				URL temp_url; 
				try{
					temp_url = new URL(link);
					parsed_list.set(x, temp_url.toString()); 
				}
				catch(MalformedURLException e)
				{
					try{ 
						temp_url = new URL((url.getProtocol()+"://"+url.getHost()+(url.getPort()!=-1 ? (":"+url.getPort()) : "")
								+ (url.getPath().isEmpty() ? "/" : "")+url.getPath()+link).trim()); 
						parsed_list.set(x, temp_url.toString()); 
					}
					catch(MalformedURLException e1)
					{
						Logger.error(e1.toString()); 
						found_invalid = true; 
						parsed_list.set(x, null); 
						continue; 
					}
				}
				int dot_pos = temp_url.getPath().lastIndexOf('.'); 
				String type = "none"; 
				if(dot_pos!=-1)
					type = temp_url.getPath().substring(dot_pos).trim(); 
				if(link.endsWith(";") || !temp_url.getProtocol().equals("http") || !(type.equals(".html") || (type.equals("none") ||
						type.equals(".xml") || type.equals(".rss"))))
				{
					parsed_list.set(x, null);
				}
			}
			
			//Add the list of outgoing links to Page
			
			ArrayList<String >final_list = new ArrayList<String>(); 
			for(int x=1;x<parsed_list.size();x++)
			{
				if(parsed_list.get(x)!=null)
					final_list.add(parsed_list.get(x));
			}
			page.outgoing_urls = final_list;
		}
			
		return data; 
	}
	
	/*
	 * @param page_updated True if page was updated this crawl.
	 *
	 */
	static boolean updateChannels(String data, Page page, boolean page_updated)
	{	
		boolean matched = false;  
		
		//If the page data was not obtained from a URL and the page has not been
		//modified, retrieve it from the database
		if(data==null && !page_updated)
		{
			data = db.retrievePageData(page.url); 
			Logger.log((data!=null) ? "Successfully retrieved from database.\n" : ""); 
		}
		else
			return false; 
		
		//If a channel has been deleted, we need to update page.channels
		if(channel_deleted)
		{
		ArrayList<String> tmp = new ArrayList<String>(page.channels.size()); 
			for(String channel : page.channels)
			{
				if(channels.contains(channel))
					tmp.add(channel); 
			}
			page.channels = tmp; 
		}
		
		//Will cease checking if the page is matched to all channels
		HashSet<String> temp_channels = (HashSet<String>) channels.clone();
	
		//Hash current page channel set
		HashSet<String> page_channels = new HashSet<String>();
		page_channels.addAll(page.channels);
		
		//Match all XPaths against the document
		//TODO: modify XPathEngine constructor for efficiency
		ArrayList<String> list = new ArrayList<String>(); 
		list.addAll(xpath_map.keySet());
		String[] xpaths = list.toArray(new String[list.size()]);
		boolean[] results = new boolean[xpaths.length]; 
		
		XPathEngine engine = new XPathEngine(xpaths); 
		try {
			System.out.println("trying"); 
			Document doc = DocumentCreator.convertToDocument(page, data);
			if(doc==null)
			{
				Logger.log("FAILED to parse document"); 
				return false;
			}
			results = engine.evaluate(doc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Logger.error(e.toString());
		} 
		
		//For each xpath match, perform necessary updating
		for(int x=0;x<xpaths.length;x++)
		{
			String xpath; 
			//If XPath valid on the page
			if(results[x])
			{
				matched = true; 
				xpath = xpaths[x]; 
				String[] channel_list = xpath_map.get(xpath); 
				
				//Check each channel associated with the XPath
				for(String channel : channel_list)
				{
					//Only create channel/page association if not 
					//already present in page and if the channel
					//has not already been considered in this loop  
					//(for xpaths shared by multiple channels)
					if(!page_channels.contains(channel) && temp_channels.contains(channel))
					{
						db.addPageToChannel(page.url, channel, xpath); 
						page.channels.add(channel); 
						temp_channels.remove(channel); 
					}
				}
			}
		}
		
		return matched; 
	}
	
	private static boolean crawledThisRun(URL url)
	{
		String[] arr = url.getPath().split("/", 2);
		HashMap<String, HashSet<String>> temp_map = null; 
		HashSet<String> temp_set = null; 
		if((temp_map = crawled.get(url.getHost()))==null)
			return false; 
		if((temp_set = temp_map.get(arr[0]))==null)
			return false; 
		try{
			if(!temp_set.contains(url.getPath().substring(arr[0].length()+1)))
				return false; 
		}
		catch(StringIndexOutOfBoundsException e)
		{
			if(!temp_set.contains(""))
				return false;
		}
		return true; 
	}
	
	private static void markAsCrawled(URL url)
	{
		HashMap<String, HashSet<String>> direc_map = null;
		Pattern pat = Pattern.compile("[^/]*"); 
		Matcher mat = pat.matcher(url.getPath()); 
		String subkey = null;
		if(mat.find()) subkey = mat.group(); 
		else subkey = ""; 
		
		if((direc_map=crawled.get(url.getHost()))!=null)
		{
			HashSet<String> temp_set = null; 
			if((temp_set = direc_map.get(subkey))==null)
			{
				temp_set = new HashSet<String>(); 
				direc_map.put(subkey, temp_set);
			}
			
			try{
				temp_set.add(url.getPath().substring(subkey.length()+1));
			}
			catch(Exception e)
			{
				Logger.error(e.toString()); 
				temp_set.add(""); 
			}
		}
		else
		{ 
			HashMap<String, HashSet<String>> first_direc_map = new HashMap<String, HashSet<String>>();  
			HashSet<String> temp_set = new HashSet<String>();
			crawled.put(url.getHost(), first_direc_map);
			first_direc_map.put(subkey, temp_set); 
			try{
				temp_set.add(url.getPath().substring(subkey.length()+1));
			}
			catch(Exception e)
			{
				Logger.error(e.toString()); 
				temp_set.add(""); 
			}
		}
	}
	
	private static void isDisallowed(Page page, boolean is_new)
	{
		URL tmp;
		try {
			tmp = new URL(page.url);
		} catch (MalformedURLException e) {
			Logger.error(e.toString());
			return;
		}
		String[] arr = page.url.split("/"); 
		String robot_url = "http://"+arr[2]+"/robots.txt"; 
		Robot robot = robots.get(arr[2]); 
		
		//If we don't have an entry for the robot, acquire one
		if(robot==null) 
			robot = new Robot(arr[2]); 
		
		//Update robots if not checked
		if(!checked_robots.contains(arr[2]))
		{
			updateRobot(robot_url, robot, robot.time_last_access);
			if(modified_robots.contains(arr[2]))
			{
				//Overwrite robot data after the check, if necessary
				db.insertRobot(robot); 		
				robots.put(arr[2], robot);
			}
		}
		
		//Only overwrite page crawl params if modifications were made
		//or the page is new
		if(modified_robots.contains(arr[2]) || is_new)
		{   
			for(String str : robot.disallows)
			{
 				if(tmp.getPath().startsWith((str)))
 				{
					page.can_crawl=false; 
 				}
			}
			page.crawl_delay = robot.crawl_delay; 
		}
	}
	
	private static void updateRobot(String robot_url, Robot robot, long time_last_access)
	{
		String[] headers = null;
		if(time_last_access!=0)
		{
			headers = new String[1]; 
			try {
				headers[0] = "If-Modified-Since: "+URLMessenging.dateToStringURL(new Date(time_last_access));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} 
		}
		BufferedReader reader = null;
		try {
			reader = URLMessenging.retrieveReader(robot_url, headers);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			Logger.error(e.toString());
		} 
		checked_robots.add(robot.host); 
		
		if(reader!=null)
		{
			parseRobot(reader, robot); 
			modified_robots.add(robot.host); 
		}
	}
	
	private static void parseRobot(BufferedReader reader, Robot robot)
	{
		String line; 
		boolean record_mode=false, last_check=false; 
		
		try
		{
			while((line=reader.readLine())!=null)
			{
				line = line.replaceFirst("#.*", "").trim();
				String lc = line.toLowerCase(); 
				
				if(lc.startsWith("user-agent:"))
				{
					record_mode = false; 
					if(line.length()==11) continue; 
					line = line.substring(11).trim();
					if(line.equals("cis455crawler"))
					{
						record_mode = true; 
						last_check = true; 
						robot.disallows = new ArrayList<String>(); 
					}
					if(line.equals("*"))
					{
						record_mode = true; 
						robot.disallows = new ArrayList<String>(); 
					}
					continue; 
				}
				
				if(record_mode==true && lc.startsWith("disallow:"))
				{
					if(line.length()==9) continue;
					line = line.substring(9).trim();
					if(line.charAt(line.length()-1)=='/')
						line = line.substring(0, line.length()-1); 
					if(robot.disallows!=null)
						robot.disallows.add(line);
					continue; 
				}
				
				if(record_mode==true && lc.startsWith("crawl-delay:"))
				{
					if(line.length()==12) continue; 
					line = line.substring(12).trim(); 
					try
					{
						robot.crawl_delay = Long.parseLong(line); 
					}
					catch(Exception e)
					{}
					continue;
				}
				
				if(line.length()==0)
				{
					record_mode = false; 
					if(last_check)
						break; 
				}
			}
		}
		catch(Exception e)
		{
			Logger.error(e.toString());
		}
		
		try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Logger.error(e.toString());
		}
		
		robot.time_last_access = (new Date()).getTime();
	}
	
	public static void main(String args[])
	{
		//WARNING: THIS MUST BE REMOVED TODO
		Testing.createNewTestDB(); 
		
		long begun = System.currentTimeMillis(); 
		System.out.println("Crawling. Please wait");
		
		//Enable Logging
		Logger.log = true; 
		Logger.error = true; 
		
		if(args.length<3 || args.length>4) 
		{
			Logger.log("Invalid number of arguments to crawler. Quitting."); 
			System.exit(1); 
		}
		
		String url_start = args[0]; 
		try {
			start_url = new URL(url_start);
		} catch (MalformedURLException e1) {
			// TODO Auto-generated catch block
			Logger.error(e1.toString());
			Logger.log("Invalid starting url"); 
		} 
		
		Logger.log("CRAWLER LOG OUTPUT\n\nLast Run: "+new Date().toString()+"\n"+"Seed URL: "+url_start+"\n"); 
		Logger.error("ERROR OUTPUT\n"); 
		
		//Setup BDB Environment 
		try
		{
			db = new DatabaseWrapper(args[1]); 
		}
		catch(Exception e)
		{
			Logger.log("Failed to instantiate database. Quitting."); 
			Logger.error(e.toString()); 
			System.exit(1); 
		}
		
		//Maxsize Argument
		try
		{
			MAX_FILE_SIZE = Long.parseLong(args[2].trim())*1024*1024;
		}
		catch (NumberFormatException e)
		{
			Logger.error(e.toString()); 
			Logger.log("Invalid max file size argument. Quitting."); 
			System.exit(1); 
		}
		
		//Optional File Limit
		if(args.length==4)
		{
			try{
				max_files_retrieved = Integer.parseInt(args[3].trim()); 
			}
			catch (NumberFormatException e)
			{
				Logger.error(e.toString()); 
				Logger.log("Invalid max files retrieved argument. Quitting."); 
				System.exit(1); 
			}
		}
		
		runCrawler();
		db.close(); 
		Logger.log("done");
		Logger.terminate(); 
		System.out.println("\nDone! Total Time: ~"+((System.currentTimeMillis()-begun)/1000)+"s\nIf the Logger was enabled" +
		", refer to 'LOG' for the output."); 
		
		/*System.out.println("Robot Map:");
		for(String str : robots.keySet())
		{
			System.out.println("\n"+str);
			Robot robot = robots.get(str);
			System.out.println("\tCrawl Delay: "+robot.crawl_delay); 
			System.out.println("\tDisallowed:"); 
			for(String str2 : robot.disallows)
				System.out.println("\t\t"+str2); 
		}*/
	}
}
