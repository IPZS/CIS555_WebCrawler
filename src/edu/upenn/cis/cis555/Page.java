import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class Page {
	String url;
	ArrayList<String> outgoing_urls; 				//outgoing_urls is whitespace-separated
	ArrayList<String> channels; 					//the channels associated with the page, whitespace-separated
	int type;  										//refer to StatusCodes
	long time_last_access, crawl_delay, file_size;	
	boolean can_crawl; 								
	
	Page(String url)
	{
		this.url = url; 
		this.type = StatusCodes.OTHER_TYPE; //default to unrecognized
		time_last_access = 0;
		crawl_delay = -1; //default, no delay
		outgoing_urls = new ArrayList<String>(1); 
		channels = new ArrayList<String>(1); 
		file_size = 0; 
		can_crawl = true; 		
	}
}
