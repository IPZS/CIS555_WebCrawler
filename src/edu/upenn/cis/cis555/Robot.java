package edu.upenn.cis.cis555;

import java.util.ArrayList;
import java.util.List;

public class Robot {

	String host; 
	long time_last_access, crawl_delay; 
	ArrayList<String> disallows; 
	
	Robot(String host)
	{
		this.host = host; 
		time_last_access = 0; 
		crawl_delay = 0; 
		disallows = new ArrayList<String>(); 
	}
}
