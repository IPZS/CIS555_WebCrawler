//package edu.upenn.cis.cis555;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;

public class Logger {
	
	static BufferedWriter log_writer, error_writer; 
	static File log_file, error_file;
	static boolean log = false, error=false, log_initialized = false, error_initialized = false; 
	
	static void log(String message)
	{
		if(!log) return; 
		if(!log_initialized)
		{
			log_file = new File("LOG"); 
			log_file.delete(); 
			log_file = new File("LOG");
			try {
				log_writer = new BufferedWriter(new FileWriter(log_file));
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
			try{
			log_writer.write(message); 
			log_writer.write("\n");
			}
			catch(IOException e)
			{
				e.printStackTrace(); 
			}
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
				error_writer = new BufferedWriter(new FileWriter(error_file));
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
			try{
			error_writer.write(message); 
			error_writer.write("\n");
			}
			catch(IOException e)
			{
				e.printStackTrace(); 
			}
		}
	}
	
	static void flush()
	{
		if(log_writer!=null)
		{
			try {
				log_writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if(error_writer!=null)
		{
			try {
				error_writer.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	static void terminate()
	{
		if(log_writer!=null)
		{
			try {
				log_writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
		if(error_writer!=null)
		{
			try {
				error_writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		}
	}
}
