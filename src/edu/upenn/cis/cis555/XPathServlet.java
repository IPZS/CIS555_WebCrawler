package edu.upenn.cis.cis555;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.w3c.dom.*;

public class XPathServlet extends HttpServlet{
	
	//Features For Future Versions:
	//		- Caching functionality 
	
	private static DatabaseWrapper db = null; 
	private static HttpServletRequest request; 
	private static HttpServletResponse response; 
	private static HttpSession session;
	private static PrintWriter writer; 
	private static String current_user; 
	private static final String path_prefix = System.getProperty("user.dir");
	
	private static enum Local_Status
	{ 
		DOCUMENT_BUILD_ERROR ("DocumentBuildError: Unable to build the request document"),
		URL_DECODING_ERROR ("URLDecodingError: An error occured while attempting to decode a URL."),
		READER_ERROR ("ReaderError: An error occured while reading a stream."),
		INVALID_QUERY ("InvalidQuery: The channel squery was invalid."), 
		INVALID_CHANNEL("InvalidChannel: The requested channel was not found."), 
		CHANNEL_EMPTY("EmptyChannel: The requested channel was empty."),
		DATABASE_ERROR ("DatabaseError: The database is corrupt."),
		SUCCESS ("Success.");
		
		private final String desc; 
		
		Local_Status(String desc)
		{
			this.desc = desc;  
		}
		
		public String toString()
		{
			return desc; 
		}
	}
	
	private void initialize() throws ServletException, IOException
	{
		try {
			if(db==null)
				db = new DatabaseWrapper(path_prefix.concat(request.getParameter("db_location")));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	
		session = request.getSession(false); 
		if(session!=null)
			current_user = ((String)session.getAttribute("username")); 
		else
			current_user = null;
		
		//System.out.println(request.getRequestURI()+((request.getQueryString()!=null) ? 
			//	"?".concat(request.getQueryString()) : ""));
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		this.request = request; 
		this.response = response; 
		
		initialize(); 
		
		//Response Headers
		//Note: getWriter() will be called after
		response.setContentType("text/html"); 
		response.setCharacterEncoding("UTF-8");

		String path = request.getRequestURI(), query = request.getQueryString();
		
		//XML Cases
		if(path.startsWith("/channel") && !path.equals("/channels"))
		{
			if(session==null)
			{
				writer = response.getWriter(); 
				writer.println("<html>"); 
				printRedirect("/welcome",0);
				writer.println("</html>");
			}
			else
				printChannel(response);
			return; 
		}
		
		writer = response.getWriter(); 
		writer.println("<html>"); 
		
		//Path Nav
		if(path.equals("/"))
		{
			if(session==null)
				printFromFile(null, path_prefix.concat("/static_pages/welcome")); 
			else
				printRedirect("/dashboard",0); 
			writer.println("</html>");return; 
		}
		if(path.equals("/welcome"))
		{
			if(session==null)
				printFromFile(null, path_prefix.concat("/resources/static_pages/welcome")); 
			else
				printRedirect("/dashboard",0); 
			writer.println("</html>");return; 
		}
		if(path.equals("/dashboard"))
		{
			if(session==null)
				printRedirect("/welcome",0); 
			else
				printFromFile(null, path_prefix.concat("/resources/static_pages/dashboard")); 
			writer.println("</html>");return;
		}
		if(path.equals("/logout"))
		{
			if(session!=null)
				session.invalidate(); 
			printRedirect("/welcome",0); 
			writer.println("</html>");return;
		}
		if(path.equals("/create_account"))
		{
			if(session!=null)
				printRedirect("/dashboard",0); 
			else
				printFromFile(null, path_prefix.concat("/resources/static_pages/create_account")); 
			writer.println("</html>");return;
		}
		if(path.equals("/create_channel"))
		{
			if(session==null)
				printRedirect("/welcome",0); 
			else
				printFromFile(null, path_prefix.concat("/resources/static_pages/create_channel")); 
			writer.println("</html>");return;
		}
		if(path.equals("/channels"))
		{
			if(session==null)
				printRedirect("/welcome",0); 
			else
				printAllChannels(); 
			writer.println("</html>");return; 
		}
		if(path.startsWith("/delete_channel"))
		{
			if(session==null)
				printRedirect("/welcome",0); 
			else
			{
				if(deleteChannel())
					printRedirect("/user_channels",0); 
				else
				{
					writer.println("Deletion Failed"); 
					printRedirect("/dashboard",1); 
				}
			}
			writer.println("</html>");return;
		}
		if(path.equals("/user_channels"))
		{
			if(session==null)
				printRedirect("/welcome",0);
			else
				printUserChannels(); 
			writer.println("</html>");return; 
		}
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
		throws ServletException, IOException
	{
		this.request = request; 
		this.response = response; 
		
		initialize(); 
		
		//Response Headers
		response.setCharacterEncoding("UTF-8");
		
		writer = response.getWriter(); 
		String path = request.getRequestURI(); 
		writer.print("<html>");
		
		if(path.equals("/pass_new_account"))
		{
			String user = request.getParameter("username"), password = request.getParameter("password"); 
			if(!addUser(user, password))
			{
				//edu.upenn.cis.cis555.webserver.Logger.log("Error Code: "+status);
				writer.println("<font color=\"red\" size=\"3\">Invalid User Name or Password. " +
						"Please select alternates.</font><br>");
				printRedirect("/create_account",1); 
			}
			else
			{ 
				writer.println("Account successfully created. " +
						"You will be automatically redirected to the login page."); 
				printRedirect("/welcome",1);
			}
			writer.println("</html>");return; 
		}
		if(path.equals("/pass_login"))
		{
			if(session!=null)
			{
				printRedirect("/dashboard",0); 
				writer.println("</html>");return;
			}
			String 			user= 		URLDecoder.decode(request.getParameter("username"),"UTF-8"), 
							password=	URLDecoder.decode(request.getParameter("password"), "UTF-8"); 
			if(user==null || password==null)
			{
				printRedirect("/welcome",0); 
				writer.println("</html>");return;
			}
			if(validateUser(user, password))
			{
				session = request.getSession();
				session.setAttribute("username", user);
			}
			else
			{
				writer.println("<font color=\"red\">Invalid User Name or Password.<font><br>");
				printRedirect("/welcome",1); 
				writer.println("</html>");return; 
			}
			 
			printRedirect("/dashboard",0); 
			writer.println("</html>");return; 
		}
		if(path.equals("/pass_new_channel"))
		{
			if(session==null)
			{
				printRedirect("/welcome",0);
				writer.println("</html>");return;
			}		
			String 	xpaths = URLDecoder.decode(request.getParameter("xpaths"), "UTF-8"), 
					name = URLDecoder.decode(request.getParameter("channel_name"), "UTF-8"),
					xslt = URLDecoder.decode(request.getParameter("xslt_url"), "UTF-8");
			if(xpaths==null || xpaths.isEmpty() || name==null || name.isEmpty() || xslt==null || xslt.isEmpty())
			{
				writer.println("One of the fields provided was invalid. Please try again.");
				printRedirect("/create_channel",1); 
				writer.println("</html>");return; 
			}
			String status = null; 
			if((status = addChannel(xpaths,xslt,name)).equals("SUCCESS"))
			{
				writer.println("Channel successfully created. " +
				"You will be automatically redirected to the dashboard."); 
				printRedirect("/dashboard",1);
			}
			else
			{
				writer.println("<font color=\"red\">Invalid Channel. Not Created.<font><br>");
				printRedirect("/create_channel",1); 
			}
			writer.println("</html>");return;
		}
	}
	
	//XPATH FUNCTIONS
	
	Local_Status checkXPaths()
	{
		Map<String, String> map = request.getParameterMap(); 
		String url=null, paths=null;
		try {
			url = URLDecoder.decode(map.get("url"),"UTF-8").trim();
			paths = URLDecoder.decode(map.get("paths"),"UTF-8"); 
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Local_Status.URL_DECODING_ERROR; 
		} 
		
		if(url==null || paths==null)
			return Local_Status.URL_DECODING_ERROR; 
		
		String[] xpaths = paths.split("\\s*;\\s*");
		
		Document doc = null;
		try {
			doc = DocumentCreator.createDocument(url, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return Local_Status.DOCUMENT_BUILD_ERROR; 
		} 
		
		if(doc == null)
			return Local_Status.DOCUMENT_BUILD_ERROR; 
		
		XPathEngine engine = new XPathEngine(xpaths);
		boolean[] results = engine.evaluate(doc); 
		
		StringBuffer buff = new StringBuffer(); 
		for(int x=0;x<results.length;x++) 
			buff.append(xpaths[x]+" ("+results[x]+")\n");
		writer.print(buff.toString());
		
		return Local_Status.SUCCESS; 
	}
	
	//PRINTING FUNCTIONS
	
	void printFromFile(String prefix, String filename)
	{
		BufferedReader reader = null;
		if(prefix!=null)
			writer.println(prefix); 
		try {
			reader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return; 
		} 
		String line; 
		try {
			while((line=reader.readLine())!=null)
				writer.println(line);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	void printAllChannels()
	{
		String[] names;
		try {
			names = db.retrieveAllChannelNames();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		for(String name : names)
			writer.println("<a href=\"/channel?ch_name="+name+"\">"+name+"</a><br>");
		writer.println("<br><a href=\"/dashboard\">Back To Dashboard</a>");
	}
	
	void printUserChannels()
	{
		if(current_user==null)
		{
			printRedirect("/welcome",0);
			return; 
		}
		String[] names = db.retrieveUserChannelNames(current_user);
		for(String name : names)
		{
			writer.println(String.format("<a href=\"/channel?ch_name=%s\">%s</a>"+
										"       <a href=\"/delete_channel?channel=%s\">Delete</a><br>"
										,name,name,name)); 
		}
			writer.println("<br><a href=\"/dashboard\">Back To Dashboard</a>"); 
	}
	
	Local_Status printChannel(HttpServletResponse response)
	{
		String query = request.getQueryString(); 
		if(query==null) return Local_Status.INVALID_QUERY; 
		
		StringBuffer buff = new StringBuffer(); 
		
		//Obtain the requested channel name and validate
		int check = query.indexOf("ch_name="); 
		if(check==-1)
			return Local_Status.INVALID_QUERY; 
		if((check+8)>=query.length()) 
			return Local_Status.INVALID_QUERY; 
		int x = 8; 
		char valid = query.charAt(check+x); 
		while((check+x)<query.length() && ((valid = query.charAt(check+x))!='&'))
		{
			buff.append(valid);
			x++;
		}
		String channel = buff.toString();
		if(!channel.matches("[\\p{Alnum}]+"))
			return Local_Status.INVALID_CHANNEL; 
		
		//Check the db for the channel and validate
		String[] pages;
		try {
			pages = db.retrieveChannelData(channel);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return Local_Status.DATABASE_ERROR; 
		} 
		if(pages.length<=1)
			return Local_Status.CHANNEL_EMPTY;
		
		//Prints pages associated with the channel to the writer 
		String xsl = pages[0]; 
		ArrayList<String> data_locs = new ArrayList<String>(pages.length); 
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		response.setCharacterEncoding("UTF-8"); 
		response.setContentType("text/xml"); 
		try {
			writer = response.getWriter();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} 
		
		writer.println("<documentcollection>"); 
		for(int y=1;y<pages.length;y++)
		{
			URL url = null; 
			Page page = null; 
			String data = null;
			try{
			url = new URL(pages[y]); 
			page = db.retrievePageMetadata(url); 
			
			//WARNING: EXCLUDES NON-XML DOCUMENTS
			if(!page.type.name().equals("XML"))
				continue;
			
			//WARNING: NON UTF-8 Documents will be displayed as UTF-8
			//if(!page.encoding.equals("UTF-8"))
				//System.out.println("WARNING: Printing non UTF-8 XML using UTF-8 encoding."); 
			
			data = db.retrievePageData(page); }
			catch(Exception e)
			{
				//System.out.println("Error On URL: "+pages[y]); 
				e.printStackTrace(); 
				return Local_Status.DATABASE_ERROR; 
			}
			if(data!=null)
			{
				writer.println	(String.format("<document crawled=\"%s\" location=\"%s\">",
								formatter.format(new Date(page.time_last_access)),
								page.url.toString())); 
				BufferedReader reader = new BufferedReader(new StringReader(data)); 
				String line; 
				try {
					while((line = reader.readLine())!=null)
					{
						//WARNING: IGNORING XML PROCESSING INSTRUCTIONS
						if(!line.trim().startsWith("<?"))
							writer.println(line);
					}
				} catch (IOException e) {
					e.printStackTrace();
					return Local_Status.READER_ERROR; 
				}
				writer.println("</document>");
			}
		}
		writer.println("</documentcollection>"); 
		
		return Local_Status.SUCCESS;
	}
	
	void printRedirect(String path, int pause)
	{
		writer.println("<head><meta http-equiv=\"REFRESH\" " +
				"content=\""+pause+";url="+path+"\"></meta></head>");
	}
	
	//DB MODIFICATION FUNCTIONS
	
	boolean addUser(String user, String password)
	{
		try {
			if(db.addUser(user, password).name().equals("SUCCESS"))
				return true; 
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false; 
	}
	
	String addChannel(String paths, String xsl, String channel)
	{
		String[] paths2 = paths.split("\\s*;\\s*");
		for(String path : paths2)
		{
			path = (XPathEngine.isValid(path)) ? path : ""; 
			path = path.trim(); 
		}
		ArrayList<String> list = new ArrayList<String>(paths2.length); 
		for(String path : paths2)
			if(!path.isEmpty()) list.add(path);
		String[] paths3 = new String[list.size()];
		list.toArray(paths3); 
		try {
			return(db.addChannel(paths3, xsl, channel, current_user).name());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null; 
		} 
	}
	
	boolean validateUser(String user, String password)
	{
		boolean output = false; 
		try {
			output = db.validateUser(user, password);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return output; 
	}
	
	boolean deleteChannel() 
	{
		String query = request.getQueryString(); 
		StringBuffer buff = new StringBuffer(); 
		
		//Obtain the requested channel name and validate
		int check = query.indexOf("channel="); 
		if(check==-1)
			return false; 
		if((check+8)>=query.length()) 
			return false; 
		int x = 8; 
		char valid = query.charAt(check+x); 
		while((check+x)<query.length() && ((valid = query.charAt(check+x))!='&'))
		{
			buff.append(valid);
			x++;
		}
		String channel = buff.toString();
		try {
			return db.deleteChannel(current_user, channel);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false; 
	}
	
	public void destroy()
	{
		if(db!=null)
		{
			try {
				db.close();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
		super.destroy(); 
	}
}
	
	