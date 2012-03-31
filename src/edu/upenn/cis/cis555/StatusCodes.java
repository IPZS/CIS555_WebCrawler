package edu.upenn.cis.cis555;

public class StatusCodes {

	static final int 	
	OTHER_TYPE = 6,				//Indicates a file that should not be considered
	RSS=5,						//Indicates an RSS file
	XML=4, 						//Indicates an XML file
	HTML=3,						//Indicates an HTML file
	EXCEPTION=2,				//Exception
	ERROR=1,					//Error state
	SUCCESS=0,					//Success
	USER_INVALID=-1, 			//Invalid characters or length
	PASSWORD_INVALID=-2, 		//Invalid characters or length
	USER_ALREADY_EXISTS=-3, 	//Username reserved
	INVALID_LOGIN_ATTEMPT=-4, 	//Username doesn't exist or password is invalid
	ASSOCIATION_EXISTS=-5, 		//The association has been previously established
	INVALID_CHANNEL_NAME=-6,	//Invalid characters or length
	FILE_NOT_FOUND=-7,			//The desired file could not be found.
	FILE_NOT_HTMLXML=-8;		//File is not html or xml type
}
