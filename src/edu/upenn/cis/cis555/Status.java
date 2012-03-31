package edu.upenn.cis.cis555;

public class Status {
	
	enum Code
	{
		OTHER_TYPE 				("A file type that should be ignored by the crawler (i.e. not XML, HTML, or RSS)"),
		RSS 					("File type is RSS"),
		XML 					("File type is XML"),
		HTML 					("File type is HTML"),
		EXCEPTION 				("A caught exception occured."),
		ERROR					("Operation resulted in an error state."),
		SUCCESS					("Operation was successful"),
		USER_INVALID			("The provided username contains invalid characters or is above the maximum length."),
		PASSWORD_INVALID 		("The provided password contains invalid characters or is above the maximum length."),
		USER_ALREADY_EXISTS		("The desired username is already reserved by another user."),
		INVALID_LOGIN_ATTEMP 	("The username does not exist or the password was invalid."),
		ASSOCIATION_EXISTS		("This association already exists in the system,"),
		INVALID_CHANNEL_NAME	("The provided password contains invalid characters or is above the maximum length."),
		FILE_NOT_FOUND			("The requested resource could not be found in the system."),
		FILE_NOT_HTMLXML		("The file is not an HTML or XML type."); 
		
		private String mssg; 
		
		private Code(String mssg)
		{
			this.mssg = mssg; 
		}
		
		public String toString()
		{
			return mssg; 
		}
	}
}
