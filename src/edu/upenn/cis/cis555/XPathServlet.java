package edu.upenn.cis.cis555;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.net.URLDecoder;
import java.util.Map;
import org.w3c.dom.*;

public class XPathServlet extends HttpServlet{

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		response.setContentType("text/html"); 
		response.setCharacterEncoding("utf-8"); 
		response.getWriter().println("<html>Sanjay Paul (sanjayp@seas.upenn.edu)<br><form action=\"http://"+
				request.getServerName()+":"+request.getServerPort()+"/passargs\" method=\"post\">" +
						"Document URL <input type=\"text\" name=\"url\"><br>" +
						"XPaths (separate with semicolons) <input type=\"text\" name=\"paths\"><br>" +
						"<input type=\"submit\"><input type=\"reset\">" +
						"</form></html>");
		response.flushBuffer(); 
	}
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		Map<String, String> map = request.getParameterMap(); 
		String url = URLDecoder.decode(map.get("url"),"UTF-8").trim(); 
		String paths = URLDecoder.decode(map.get("paths"),"UTF-8"); 
		String[] xpaths = paths.split(";");
		
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
}
	
	