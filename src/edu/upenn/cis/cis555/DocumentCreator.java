package edu.upenn.cis.cis555;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;

import javax.activation.MimetypesFileTypeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.SAXException;

public class DocumentCreator {
	
	private static final int ERROR=-1, XML=1, HTML=2; 
	
	/*
	 * Parses a given URL (HTTP or absolute directory path) and creates a DOM-style Document node
	 * from an XML or HTML file. 
	 * 
	 * @param url A String indicating either a directory path (absolute or relative to the accessible root)
	 * or an HTTP URL pointing to the resource to be converted to a Document. 
	 * 
	 * @param file_name The desired filename if the file should be stored. Pass in null to specify
	 * a temporary file that should be deleted on exit. 
	 * 
	 * @return The Document matching the URL or null on error. 
	 */
	static Document createDocument(String url, String file_name) throws IOException
	{
		String location = null, machine=null; 
		Socket client = null;
		File file = null; 
		Document doc = null;
		InputStream input = null; 
		int head_check; 
		boolean is_xml=false; 
		
		//handle URL case
		if(url.startsWith("http://"))
		{	
			//Establish socket and reader/writer
			//TODO: Confirm this works
			URL urlref = new URL(url);
			int port = urlref.getPort(); 
			if(port==-1)
				port = 80; 
			machine = urlref.getHost()+":"+String.valueOf(urlref.getPort()); 
			
			location = urlref.getPath();  
			
			client = new Socket(machine,80);
			PrintWriter writer = new PrintWriter(client.getOutputStream()); 
			BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
			
			//Send HEAD request to check url validity 
			head_check = checkHead(location, machine, reader, writer); 
			if(head_check==ERROR) return null; 
			if(head_check==XML) is_xml=true;
			else is_xml=false; 
			
			//Send GET request to obtain and save file
			if(file_name==null) file_name = "TEMP"; 
			file = outputToFile(location, machine, file_name, reader, writer);
			if(file==null) return null; 
			if(file_name.equals("TEMP")) file.deleteOnExit(); 
		}
		else
		{
			//otherwise, default to local file
			file = new File(url); 
			if(file == null || !file.isFile())
				return null; 
			else
			{
				MimetypesFileTypeMap typemap = new MimetypesFileTypeMap(); 
				if(typemap.getContentType(file).contains("text/html")) is_xml=false;
				else
				{
					if(typemap.getContentType(file).contains("text/xml")) is_xml = true;
					else return null; 
				}
			}
		} 
		
		//JTidy for HTML
		if(!is_xml)
		{
			input = new FileInputStream(file);
			Tidy tidy = new Tidy();
			tidy.setTidyMark(false); 
			tidy.setShowWarnings(false);
			doc = tidy.parseDOM(input, null); 
		}
		//DocumentBuilder for XML
		else
		{
			try {
				doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
			} catch (SAXException e) {
				e.printStackTrace();
				return null; 
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
				return null; 
			} catch (FactoryConfigurationError e) {
				e.printStackTrace();
				return null; 
			}  
		}
		
		return doc; 
	}
	
	static int checkHead(String location, String machine, BufferedReader reader, PrintWriter writer)
	{ 
		//first perform check with HEAD to determine validity and mimetype 
		try
		{ 	
			writer.println("HEAD "+location+" HTTP/1.1\r\nHost: "+machine+"\r\nConnection: keep-alive\r\n\r\n");
			writer.flush(); 
			String line; 
			
			while((line=reader.readLine())!=null && line.length()>0)
			{
				line = line.toLowerCase();
				if(line.startsWith("http/"))
				{
					if(!line.contains("200"))
						return ERROR;  
				}
				if(line.contains("content-type"))
				{
					if(line.contains("text/xml") || line.contains("application/xml"))
						return XML; 
					else
					{
						if(line.contains("text/html"))
							return HTML; 
						else
							return ERROR;  
					}
				}
				else return ERROR; 
			}
		}
		catch(Exception e)
		{
			e.printStackTrace(); 
		}
		
		return ERROR; 
	}
	
	static File outputToFile(String location, String machine, String file_name, BufferedReader reader, 
			PrintWriter writer)
	{
		File file = null; 
		
		//output contents to file
		try
		{
			writer.println("GET "+location+" HTTP/1.1\r\nHost: "+machine+"\r\nConnection: close\r\n\r\n");
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
			e.printStackTrace(); 
		}
		
		return file; 
	}
}
