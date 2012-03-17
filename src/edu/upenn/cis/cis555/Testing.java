package edu.upenn.cis.cis555; 

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;

public class Testing {
	
	private static void performDocumentParseTest(String[] locations) throws IOException
	{
		for(String location : locations)
		{
			Document doc = XPathServlet.createDocument(location);
			XPathEngine engine = new XPathEngine(new String[0]);
			System.out.println("URL: "+location);
			engine.printTree(doc); 
		}
	}
	
	private static void performIsValidCheck(String[] arr)
	{
		XPathEngine engine = new XPathEngine(arr);
		for(int x=0;x<arr.length;x++)
			System.out.println(arr[x]+" ("+engine.isValid(x)+")");
	}

	public static void main(String args[])
	{
		//isValid Test
		
		//All should eval to true
		String[] strs1 = {"/foo/bar/xyz",
						 "/foo/bar[@att=\"123\"]",
				         "/xyz/abc[contains(text(),\"someSubstring\")]",
				         "/a/b/c[text()=\"theEntireText\"]",
				         "/blah[anotherElement]",
				         "/this/that[something/else]",
				         "/d/e/f[foo[text()=\"something\"]][bar]",
				         "/a/b/c[text() = \"whiteSpacesShouldNotMatter\"]",
				         "/a/foo/bar[something/else[@att=\"value\"]]",
				         "/foo/bar[something/else[@att=\"value\"]]/fun/sun",
				         "/a/b[foo[text()=\"#$(/][]\"]][bar]/hi[@asdf=\"#$(&[]\"][this][is][crazy]"}; 
		
		//All should eval to false
		String[] strs2 = 	{"",
							" ",
							"no_backslash",
							"/foo/bar[text()=",
							"/foo/bar[text()=\"this is okay\"][text()=\"this is not"};
		
		System.out.println("\n\nIsValid True Checks:\n"); 
		performIsValidCheck(strs1); 
		System.out.println("\n\nIsValid False Checks:\n"); 
		performIsValidCheck(strs2); 
		
		//Document Parsing Test
		
		//Parses the document at each url and prints the document tree for each to System.out
		String[] locations = 	{"resources/hello.html",
								"http://crawltest.cis.upenn.edu/",
								"http://crawltest.cis.upenn.edu/misc/weather.xml",
								"http://crawltest.cis.upenn.edu/misc/eurofxref-daily.xml"}; 
		
		System.out.println("\n\nDocument Parsing Test\n"); 
		try {
			performDocumentParseTest(locations);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
}
