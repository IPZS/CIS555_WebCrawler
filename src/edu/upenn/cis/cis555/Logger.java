package edu.upenn.cis.cis555;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Logger {
	
	private static PrintWriter log_writer, error_writer; 
	private static File log_file, error_file;
	private static int log_count = 15;  
	static boolean log = false, error=false; 
	private static boolean log_initialized = false, error_initialized = false; 
	
	static void log(String message)
	{
		if(!log) return; 
		if(!log_initialized)
		{
			log_file = new File("LOG"); 
			log_file.delete(); 
			log_file = new File("LOG");
			try {
				log_writer = new PrintWriter((new BufferedWriter(new FileWriter(log_file))));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e)
			{
				e.printStackTrace(); 
			}
			log_initialized = true; 
		}
		if(log_writer!=null)
		{
			log_writer.println(message);
			log_count--; 
		}
		//Autoflush every 15 lines 
		if(log_count==0)
		{
			log_writer.flush(); 
			log_count = 15; 
		}
	}
	
	static void error(String message)
	{
		if(!error) return; 
		if(!error_initialized)
		{
			error_file = new File("ERROR"); 
			error_file.delete(); 
			error_file = new File("ERROR");
			try {
				error_writer = new PrintWriter(new BufferedWriter(new FileWriter(error_file)));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e)
			{
				e.printStackTrace(); 
			}
			error_initialized = true; 
		}
		if(error_writer!=null)
		{
			error_writer.print("[ERROR]"); 
			error_writer.println(message);
			error_writer.flush(); 			//NOTE: error log flushes on every message
		}
	}
	
	//Intended for use with stack trace printing 
	static PrintWriter getErrorWriter()
	{
		return error_writer; 
	}
	
	static void terminate()
	{
		if(log_writer!=null)
		{
			log_writer.close(); 
		}
		if(error_writer!=null)
		{
			error_writer.close(); 
		}
	}
}
