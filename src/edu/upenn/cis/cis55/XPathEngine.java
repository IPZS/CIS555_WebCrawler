package edu.upenn.cis.cis55;

public class XPathEngine {
	
	String[] expressions;
	boolean[] valid; 
	
	/*
	 * Constructor 
	 * 
	 * @param s An array of the XPath expression to be 
	 * evaluated. 
	 */
	XPathEngine(String[] s)
	{
		expressions = s; 
		valid = new boolean[expressions.length]; 
	}
	
	/*
	 * Uses a given DOM root node to evaluate the 
	 * passed XPath expressions' validity. 
	 * 
	 * @param d A DOM root node for the corresponding HTML/XML 
	 * 
	 * @return A boolean array of the same length as the
	 * expression array, indicating true/false depending on 
	 * their validity based on the given DOM root node. 
	 */
	boolean[] evaluate(Document d)
	{
		
	}
	
	/*
	 * @return True if the i.th XPath was valid
	 */
	boolean isValid(int i)
	{
		
	}
}
