package edu.upenn.cis.cis555; 

import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Page {
	URL url;
	ArrayList<String> outgoing_urls; 				//outgoing_urls is whitespace-separated
	ArrayList<String> channels; 					//the channels associated with the page, whitespace-separated
	Status.Code type;  										//refer to Status
	long time_last_access, crawl_delay, file_size;	
	String encoding; 
	boolean can_crawl, stored; 								
	
	Page(URL url, String encoding)
	{
		this.url = url; 
		this.type = Status.Code.FILE_NOT_HTMLXML; //default to unrecognized
		time_last_access = 0;
		crawl_delay = -1; //default, no delay
		outgoing_urls = new ArrayList<String>(1); 
		channels = new ArrayList<String>(1); 
		file_size = 0; 
		can_crawl = true;
		stored = false; 
		this.encoding = encoding; 
	}
}
