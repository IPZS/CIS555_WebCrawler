package edu.upenn.cis.cis555;

public class XPathCrawler {

	int MAX_FILE_SIZE = 0; 
	int MAX_FILES_RETRIEVED = -1; 
	
	public boolean checkRobot()
	{
		return true; 
	}
	
	public static void main(String args[])
	{
		if(args.length<3 || args.length>4) 
		{
			System.out.println("Failed to instantiate crawler. Quitting."); 
			System.exit(1); 
		}
		
		String url_start = args[0]; 
		
		//Setup BDB Environment 
		try
		{
			DatabaseWrapper storage = new DatabaseWrapper(args[1]); 
		}
		catch(Exception e)
		{
			System.out.println("Failed to instantiate database. Quitting."); 
			e.printStackTrace(); 
			System.exit(1); 
		}
		
		//Maxsize Argument
		try{
			int MAX_FILE_SIZE = Integer.parseInt(args[2].trim()); 
		}
		catch (NumberFormatException e)
		{
			e.printStackTrace(); 
			System.out.println("Invalid max file size argument. Quitting."); 
			System.exit(1); 
		}
		
		//Optional File Limit
		if(args.length==4)
		{
			try{
				int MAX_FILES_RETRIEVED = Integer.parseInt(args[3].trim()); 
			}
			catch (NumberFormatException e)
			{
				e.printStackTrace(); 
				System.out.println("Invalid argument to max number of files to parse. Quitting."); 
				System.exit(1); 
			}
		}
		
		//Instatiate Engine
		XPathEngine
	}
}
