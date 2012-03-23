//package edu.upenn.cis.cis555; 

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class URLMessenging {

	private static PrintWriter writer; 
	private static BufferedReader reader; 
	private static Socket client = null; 
	
	//TODO: Increase security of reads (i.e. read limits) 
	
	static URL setupReaderWriter(String url)
	{
		//Establish socket and reader/writer
		URL urlref = null; 
		try {
			urlref = new URL(url);
		} catch (MalformedURLException e1) {
			Logger.error(e1.toString());
			return null; 
		}
		
		try
		{
			client = new Socket(urlref.getHost(),80);
			if(client!=null)
			{
				writer = new PrintWriter(client.getOutputStream()); 
				reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
				client.setSoTimeout(2000); 
			}
		}
		catch(Exception e)
		{
			Logger.error(e.toString());
			return null; 
		}
		
		return urlref; 
	}
	
	/* Performs a Head check 
	 * 
	 * @return StatusCode indicating the file type or error condition. 
	 */
	static HashMap<String, String> checkHead(String url, List<String> headers) throws Exception
	{ 
		URL urlref = setupReaderWriter(url); 
		if(urlref==null) return null; 
		HashMap<String, String> header_map = new HashMap<String, String>(); 
		
		try
		{ 	
			String request = "\nRequest:\n\nHEAD "+(urlref.getPath().isEmpty() ? "/" : urlref.getPath())
			+" HTTP/1.0\r\nHost: "+urlref.getHost()+"\r\n";
			writer.println("HEAD "+(urlref.getPath().isEmpty() ? "/" : urlref.getPath())+" HTTP/1.0\r\nHost: "+urlref.getHost()+"\r\n");
			if(headers!=null)
			{
				for(String str : headers)
				{
					writer.println(str+"\r\n"); 
					request.concat(str+"\r\n"); 
				}
			}
			//System.out.print(request); 
			writer.println("User-Agent: cis455crawler\r\n");
			writer.println("Connection: close\r\n\r\n"); 
			writer.flush(); 
			String line=reader.readLine();
			//System.out.println("\nHEAD Check(links): "+url+"\n"); 
			//System.out.println(line); 
			if(!line.startsWith("HTTP"))
				return null; 
			header_map.put("initial", line); 
			
			while((line=reader.readLine())!=null && line.length()>0)
			{
				//System.out.println(line); 
				Pattern pat = Pattern.compile("[A-Za-z\\-]*:");
				Matcher mat = pat.matcher(line);
				if(mat.find())
				{
					int offset = mat.end()+1;
					if(offset<=line.length()-1)
					{
						String match = mat.group(); 
						String key = match.substring(0,match.length()-1).toLowerCase(); 
						String value = line.substring(offset).trim(); 
						
						// appends to existing entry
						String prev; 
						if((prev=header_map.get(key))!=null)
							header_map.put(key, prev.concat(";").concat(value));
						// if not found, create new entry
						else
							header_map.put(key, value); 
					}
				}
			}
			//System.out.println(); 
		}
		catch(Exception e)
		{
			Logger.error(e.toString()); 
		}
		
		writer.close(); 
		try {
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Logger.error(e.toString());
			return header_map; 
		} 
		
		return header_map; 
	}
	
	
	/*
	 * @return File reference or null on error
	 */
	static File outputToFile(String url, String file_name)
	{
		File file = null;
		
		URL urlref= setupReaderWriter(url); 
		if(urlref==null) return null; 
		
		//output contents to file
		try
		{
			writer.println("GET "+urlref.getPath()+" HTTP/1.0\r\nHost: "+urlref.getHost()
					+"\r\nConnection: close\r\n\r\n");
			writer.flush(); 
			String line=null; 
			int length = -1; 
			while((line=reader.readLine())!=null && line.length()>0)
			{ 
				line = line.toLowerCase();
				if(line.trim().startsWith("content-length:"))
						length = Integer.parseInt(line.replaceFirst("content-length:","").trim());
			}
			if(length==-1)
				return null; 
			file = new File(file_name);
			FileWriter fwriter = new FileWriter(file);
			while((line=reader.readLine())!=null)
				fwriter.write(line); 
			fwriter.flush(); 
			fwriter.close();  
		}
		catch(Exception e)
		{
			Logger.error(e.toString()); 
			return null; 
		}
		
		writer.close();
		try {
			reader.close();
		} catch (IOException e) {
			Logger.error(e.toString());
			return null; 
		} 
		
		return file; 
	}
	
	/*
	 * @return A String array. The first entry is the data and all remaining 
	 * entries are outgoing links if retrieve_links is set to true
	 */
	static ArrayList<String> outputToString(String url, boolean retrieve_links, String[] headers) throws Exception
	{
		StringBuffer data = new StringBuffer(); 
		StringBuffer links = new StringBuffer(); 
		ArrayList<String> output = null; 
		URL urlref= setupReaderWriter(url); 

		if(urlref==null) return null; 
		
		//output contents to file
		try
		{
			String path = urlref.getPath(); 
			if(path.isEmpty()) path = "/";
			writer.println("GET "+path+" HTTP/1.0\r\nHost: "+urlref.getHost()+"\r\n");
			if(headers!=null)
			{
				for(String str : headers)
					writer.println(str+"\r\n"); 
			}
			writer.println("User-Agent: cis455crawler\r\n"); 
			writer.println("Connection: close\r\n\r\n"); 
			writer.flush(); 
			String line=reader.readLine();
			if(!line.contains("200"))
				return null; 
			
			while((line=reader.readLine())!=null && line.length()>0); 
			
			if(retrieve_links)
			{
				while((line=reader.readLine())!=null)
				{
					System.out.println(line);
					Pattern pat = Pattern.compile("\\s*[Hh][Rr][Ee][Ff]\\s*=\\s*[\"']"); 
					Matcher mat = pat.matcher(line);
					while(mat.find())
					{
						int begin = mat.end(), end = mat.end();  
						if(begin>line.length()-1) continue; 
						char ch = line.charAt(begin); 
						StringBuffer temp = new StringBuffer(); 
						while((end<line.length()-1) && (ch=line.charAt(end))!=line.charAt(begin-1))
						{
							if(ch==' ')
								break; 
							temp.append(ch); 
							end++; 
						}
						if(ch==line.charAt(begin-1))
						{
							links.append(temp); 
							links.append(" "); 
						}
					}
					data.append(line); 
					data.append("\n"); 
				}
				
				output = new ArrayList<String>(); 
				output.add(data.toString()); 
				for(String link : links.toString().split(" "))
					output.add(link); 
			}		
			else
			{
				while((line=reader.readLine())!=null)
				{
					data.append(line); 
					data.append("\n"); 
				}
				output = new ArrayList<String>(); 
				output.add(data.toString()); 
			}
		}
		catch(Exception e)
		{
			Logger.error(e.toString()); 
			return null; 
		}
		
		writer.close();
		try {
			reader.close();
		} catch (IOException e) {
			Logger.error(e.toString());
			return null; 
		} 
		
		return output; 
	}
	
	/*
	 * @return A BufferedReader positioned at the first line of the retrieved file 
	 * or null on failure. 
	 */
	static BufferedReader retrieveReader(String url, String[] headers) throws Exception
	{
		StringBuffer data = new StringBuffer();
		URL urlref= setupReaderWriter(url); 
		if(urlref==null) return null;
		//output contents to file
		try
		{
			writer.println("GET "+urlref.getPath()+" HTTP/1.0\r\nHost: "+urlref.getHost()+"\r\n");
			if(headers!=null)
			{
				for(String str : headers)
					writer.println(str+"\r\n"); 
			}
			writer.println("User-Agent: cis455crawler\r\n"); 
			writer.println("Connection: close\r\n\r\n"); 
			writer.flush(); 
			String line=reader.readLine();
			if(!line.contains("200"))
				return null; 
			while((line=reader.readLine())!=null && line.length()>0);
		}
		catch(IOException e)
		{
			Logger.error(e.toString());
			return null; 
		}
			
		return reader; 
	}
	
	static String dateToStringURL(Date date) throws Exception
	{
		SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"); 
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		return formatter.format(date); 
	}
	
	static Date extractDateURL(String source) throws Exception
	{
		Date date = null; 
		
			SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ROOT);
			SimpleDateFormat formatter1 = new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss zzz",Locale.ROOT);
			SimpleDateFormat formatter2 = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy",Locale.ROOT); 
			
			formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
			formatter1.setTimeZone(TimeZone.getTimeZone("GMT"));
			formatter2.setTimeZone(TimeZone.getTimeZone("GMT"));
			
			try {
				date = formatter.parse(source);} 
			catch (ParseException e) {Logger.error(e.toString());}
			try {
				if(date==null)
					date = formatter1.parse(source); }
			catch(ParseException e){Logger.error(e.toString());}
			try{
			if(date==null)
				date = formatter2.parse(source); }
			catch(ParseException e){Logger.error(e.toString());}
		
		return date; 
	}
}
