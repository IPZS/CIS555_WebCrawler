//package edu.upenn.cis.cis555;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;
import org.w3c.dom.*;

public class XPathServlet extends HttpServlet{
	
	private static DatabaseWrapper db = null; 
	private static final String path_prefix = System.getProperty("user.dir");
	
	/*protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		response.setContentType("text/html"); 
		response.setCharacterEncoding("utf-8"); 
		response.getWriter().println("<html>Sanjay Paul (sanjayp@seas.upenn.edu)<br><form action=\"http://"+
				request.getServerName()+":"+request.getServerPort()+"/passargs\" method=\"post\"><br>" +
						"Document URL <input type=\"text\" name=\"url\"><br>" +
						"XPaths (separate with semicolons) <input type=\"text\" name=\"paths\"><br>" +
						"<input type=\"submit\"><input type=\"reset\">" +
						"</form></html>");
		response.flushBuffer(); 
	}*/

	//Milestone #1 (deprecated) form
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try {
			if(db==null)
				db = new DatabaseWrapper(path_prefix.concat(request.getParameter("db_location")));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		response.setContentType("text/html"); 
		response.setCharacterEncoding("UTF-8");
		String path = request.getRequestURI(), query = request.getQueryString();
		System.out.println(request.getRequestURI()+"?"+request.getQueryString());
		System.out.println(request.getParameter("ch_name"));
		
		//Path Nav
		if(path.equals("/welcome"))
		{
			printFromFile(path_prefix.concat("/resources/static_pages/welcome"),response.getWriter()); 
			return; 
		}
		if(path.equals("/dashboard"))
		{
			printFromFile(path_prefix.concat("/resources/static_pages/dashboard.html"),response.getWriter()); 
			return;
		}
		if(path.equals("/create_account"))
		{
			printFromFile(path_prefix.concat("/resources/static_pages/create_account"),response.getWriter()); 
			return;
		}
		if(path.equals("/create_channel"))
		{
			printFromFile(path_prefix.concat("/resources/static_pages/create_channel.html"),response.getWriter()); 
			return;
		}
		if(path.equals("/channels"))
		{
			printAllChannels(response.getWriter()); 
			return; 
		}
		if(path.startsWith("/channel?"))
		{
			
		}
		response.flushBuffer(); 
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try {
			if(db==null)
				db = new DatabaseWrapper(path_prefix.concat(request.getParameter("db_location")));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Map<String, String> map = request.getParameterMap(); 
		String url = URLDecoder.decode(map.get("url"),"UTF-8").trim(); 
		String paths = URLDecoder.decode(map.get("paths"),"UTF-8"); 
		String[] xpaths = paths.split("\\s*;\\s*");
		
		Document doc = DocumentCreator.createDocument(url, null); 
		
		if(doc == null)
		{
			response.getWriter().println("Failed to build document."); 
			response.flushBuffer(); 
			return; 
		}
		
		XPathEngine engine = new XPathEngine(xpaths);
		boolean[] results = engine.evaluate(doc); 
		
		StringBuffer buff = new StringBuffer(); 
		for(int x=0;x<results.length;x++) 
			buff.append(xpaths[x]+" ("+results[x]+")\n");
		response.getWriter().print(buff.toString()); 
		response.flushBuffer(); 
	}
	
	static void printFromFile(String filename, PrintWriter writer)
	{
		BufferedReader reader = null;
		boolean is_tagged = filename.endsWith(".html"); 
		try {
			reader = new BufferedReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return; 
		} 
		if(!is_tagged)
			writer.println("<htmL>"); 
		String line; 
		try {
			while((line=reader.readLine())!=null)
				writer.println(line);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(!is_tagged)
			writer.println("</html>"); 
	}
	
	static void printAllChannels(PrintWriter writer)
	{
		String[] names = db.retrieveAllChannelNames(); 
		writer.println("<html>"); 
		for(String name : names)
		{
			System.out.println(name);
			writer.println("<a href=\"/channel?ch_name="+name+"\">"+name+"</a><br>"); 
		}
		writer.println("</html>");
	}
}
	
	