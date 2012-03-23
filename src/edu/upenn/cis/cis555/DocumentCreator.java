//package edu.upenn.cis.cis555;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.tidy.Tidy;
import org.xml.sax.SAXException;

public class DocumentCreator {
	
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
	public static Document createDocument(String url, String file_name) throws IOException
	{
		String location = null, machine=null; 
		Socket client = null;
		File file = null; 
		Document doc = null;
		InputStream input = null;
		boolean is_xml=false; 
		
		//handle URL case
		if(url.startsWith("http://"))
		{	
			//Send HEAD request to check url validity 
			HashMap<String, String> head_check = null;
			try {
				head_check = URLMessenging.checkHead(url, null);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} 
			if(head_check==null) return null; 
			if(head_check.isEmpty()) return null; 
			
			if(!head_check.get("initial").contains("200")) return null;
			
			for(int x=1;x<head_check.size();x++)
			{
				String line= head_check.get("content-type"); 
				if(line!=null)
				{
					if(line.contains("text/xml") || line.contains("application/xml"))
						is_xml=true; 
					else
					{
						if(!line.contains("text/html"))
							return null; 
					}
				}
			}
			
			//Send GET request to obtain and save file
			if(file_name==null) file_name = "TEMP"; 
			file = URLMessenging.outputToFile(url, file_name);
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
				Logger.error(e.toString());
				return null; 
			} catch (ParserConfigurationException e) {
				Logger.error(e.toString());
				return null; 
			} catch (FactoryConfigurationError e) {
				Logger.error(e.toString());
				return null; 
			}  
		}
		
		return doc; 
	}
	
	/*
	 * Retrieves a Page from the provided database and creates a DOM-style Document node 
	 * corresponding to its data. 
	 * 
	 * @param url A String that is a key into the database for the desired page.  
	 * 
	 * @param db The DatabaseWrapper from which to retrieve the page.
	 * 
	 * @return The Document corresponding to the Page or null on error. 
	 */
	public static Document convertToDocument(Page page, String data) throws IOException
	{
		Document doc = null;
		InputStream input = null;
		
		input = new ByteArrayInputStream(data.getBytes("UTF-8")); 
		
		//JTidy for HTML
		if(page.type==StatusCodes.HTML)
		{
			long start = System.currentTimeMillis(); 
			Tidy tidy = new Tidy();
			tidy.setTidyMark(false); 
			tidy.setShowWarnings(false);
			doc = tidy.parseDOM(input, null); 
			System.out.println("JTidy Parse Time: "+(System.currentTimeMillis()-start)+"ms"); 
		}
		//DocumentBuilder for XML
		else
		{
			long start = System.nanoTime(); 
			try { 
				doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			} catch (FactoryConfigurationError e) {
				// TODO Auto-generated catch block
				Logger.error(e.toString());
			}
			System.out.println("DocBuilder Parse Time: "+(System.currentTimeMillis()-start)+"ms"); 
		}
		
		return doc; 
	}
	
}
