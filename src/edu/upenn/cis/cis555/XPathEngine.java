package edu.upenn.cis.cis555; 

import java.util.regex.*;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XPathEngine {
	
	String[] expressions;
	
	/*
	 * An engine allowing for the validation of a specified set of
	 * XPath expressions against XML/HTML documents. 
	 * 
	 * @param s An array of XPath expressions to be 
	 * evaluated. 
	 */
	public XPathEngine(String[] s)
	{
		if(s==null)
			throw new IllegalArgumentException("The passed expression array pointer is null.");
		expressions = s; 
	}
	
	/*
	 * Checks a given document against the set of XPath 
	 * expressions associated with this XPathEngine. 
	 * 
	 * @param d A DOM root node for the corresponding HTML/XML 
	 * 
	 * @return A boolean array of the same length as the
	 * expression array, indicating true/false depending on 
	 * their validity based on the given DOM root node. 
	 */
	public boolean[] evaluate(Document d)
	{
		//printTree(d); 
		
		Node parent = d; 
		
		boolean[] arr = new boolean[expressions.length]; 
		
		for(int x=0;x<expressions.length;x++)
		{
			if(expressions[x].isEmpty() || !isValid(x))
			{
				arr[x] = false; 
				continue; 
			}
			
			String path = expressions[x].trim().substring(1);
			String node_name = getNextPathNodeName(path);
			
			if(parent.getNodeName().equals("#document"))
				parent = parent.getFirstChild(); 
			
			if(!node_name.equals(parent.getNodeName()))
				arr[x] = false; 
			else
				arr[x] = checkNode(path,parent); 
		}
		
		return arr;  
	}
	
	/*
	 * @return True if the i.th XPath was valid
	 */
	public boolean isValid(int i)
	{
		if((i<0 || i>expressions.length-1) || expressions[i]==null)
			return false;
		
		//pass to recursive call
		return isPath(expressions[i]); 
	}
	
	boolean checkNode(String path, Node parent)
	{
		//System.out.println(path); 
		//reached the end of the path
		if(path.isEmpty()) return true;
		
		String node_name = getNextPathNodeName(path);
		
		//modify the path
		if(node_name.length()<path.length())
			path = path.substring(node_name.length());  
		else 
			path = ""; 
		
		//check the tests
		while(!path.isEmpty() && path.charAt(0)=='[')
		{
			String test = getNextTest(path);
			if(!checkTest(parent, test))
				return false; 
			if(test.length()<path.length())
				path = path.substring(test.length()); 
			else
				path=""; 
		}
		
		//check the next node
		Node child = null; 
		
		if(!path.isEmpty()) 
		{
			path = path.substring(1); 
			child = retrieveChild(parent, path);
			if(child==null)
				return false;
		}
		
		//proceed recursively on next node
		return checkNode(path,child); 
	}
	
	boolean checkTest(Node node, String test)
	{
		test = test.substring(1);
		test = test.substring(0,test.length()-1); 
		
		//System.out.println("Node: "+node.getNodeName()); 
		//System.out.println("Condition: "+test); 
		
		String query = null; 
		Pattern pat = Pattern.compile("\"[^\"]*\""); 
		Matcher mat = pat.matcher(test); 
		if(mat.find())
		{
			query = mat.group(); 
			query = query.substring(1); 
			query = query.substring(0,query.length()-1);
		}
		
		String node_val = null; 
		NodeList children = node.getChildNodes(); 
		for(int x=0;x<children.getLength();x++)
		{
			Node val = children.item(x); 
			if(val!=null)
			{
				node_val = val.getNodeValue();
				if(node_val!=null)
					break; 
			}
		}
		//System.out.println("Query: "+query); 
		//System.out.println("Node Value: "+node_val); 
		
		if(test.trim().startsWith("text()"))
		{
			if(node_val==null) return false;
			return(query.equals(node_val)); 
		}
		
		if(test.trim().startsWith("@"))
		{
			if(!node.hasAttributes())
				return false;
			String att = null; 
			Pattern pat1 = Pattern.compile("@[\\p{Alnum}:\\-_]+"); 
			Matcher mat1 = pat1.matcher(test); 
			if(mat1.find())
				att = mat1.group(); 
			att = att.substring(1);
			//System.out.println("att: "+att);
			NamedNodeMap map = node.getAttributes(); 
			Node attr = map.getNamedItem(att);
			if(attr==null) return false; 
			return(attr.getNodeValue().equals(query)); 
		}
		
		if(test.trim().startsWith("contains") && !test.trim().startsWith("contains/"))
		{
			if(node_val==null) return false;
			return(node_val.contains(query));
		}
		
		//check the node inside the test recursively 
		Node child = retrieveChild(node,test);
		if(child==null) return false;
		return checkNode(test, child); 
	}
	
	String getNextPathNodeName(String path)
	{
		Pattern pat1 = Pattern.compile("[\\p{Alnum}\\-:_]+"); 
		Matcher mat1 = pat1.matcher(path);
		if(!mat1.find()) return null; 
		return mat1.group();
	}
	
	Node retrieveChild(Node node, String path)
	{
		String node_name = getNextPathNodeName(path); 
		NodeList list = node.getChildNodes(); 
		for(int x=0;x<list.getLength();x++)
		{
			if(list.item(x).getNodeName().equals(node_name))
				return list.item(x); 
		}
		
		return null; 
	}
	
	void printTree(Document d)
	{
		System.out.println(d.getNodeName()); 
		if(d.hasChildNodes())
			printTree(d.getChildNodes(), 1); 
	}
	
	void printTree(NodeList nodes, int level)
	{
		for(int x=0;x<nodes.getLength();x++)
		{

			System.out.println("\t");
			for(int y=0;y<level-1;y++)
				System.out.print("\t"); 
			Node temp = nodes.item(x);
			if(temp.getNodeValue()!=null)
				System.out.print(temp.getNodeValue()); 
			else
				System.out.print(temp.getNodeName()); 
			if(temp.hasAttributes())
			{
				System.out.print(" (Attributes: "); 
				for(int y=0;y<temp.getAttributes().getLength();y++)
					System.out.print(temp.getAttributes().item(y).getNodeName()+ ": "
						+temp.getAttributes().item(y).getNodeValue()+ " ");
				System.out.print(")"); 
			}
			if(temp.hasChildNodes())
				printTree(temp.getChildNodes(), level+1);
		}
	}
	
	// isValid Helper Methods
	
	boolean isPath(String path)
	{	
		if(path.isEmpty()) return false; 
		if(path.charAt(0)!='/')
			return false; 
		return isStep(path.substring(1)); 	
	}
	
	boolean isStep(String s_path)
	{
		if(!s_path.isEmpty())
		{
			Pattern pat = Pattern.compile("[\\p{Alnum}\\-:_]+");
			Matcher mat = pat.matcher(s_path); 

			if(mat.find())
			{
				String nodename = mat.group(); 
				
				//not supporting "::" operation
				if(nodename.contains("::")) return false; 
				
				int index = mat.end();
				
				//remaining path info
				if(index<s_path.length()-1)
				{
					String suffix = s_path.substring(index); 
					
					//check tests
					while(!suffix.isEmpty() && suffix.charAt(0)=='[')
					{
						String test = getNextTest(suffix); 
						if(test==null) return false;
						if(!isTest(test))
							return false; 
						if(test.length()<suffix.length())
							suffix = suffix.substring(test.length()); 
						else
							suffix=""; 
					}
					
					//check for leftover path
					if(!suffix.isEmpty())
						return isPath(suffix); 
				}
				//path is ended
				return true;
			}
			else	
				return isPath(s_path); 
		}
		
		//empty (default) case
		return true;
	}
	
	String getNextTest(String path)
	{
		boolean quoted = false; 
		int opened = 0, r_index = 0; 
		for(int x=0;x<path.length();x++)
		{
			if(path.charAt(x)=='"')
			{
				if(quoted)
					quoted = false;
				else
					quoted = true;
			}
			
			r_index++; 
			if(path.charAt(x)==']' && !quoted)
			{
				if(opened==1)
				{
					opened--; 
					break;
				}
				else
				{
					if(opened!=0)
						opened--;
					else
						return null; 
				}
			}
			if(path.charAt(x)=='[' && !quoted)
				opened++;
		}
		
		if(opened>0) return null; 
		
		return path.substring(0,r_index); 
	}
	
	boolean isTest(String tester)
	{
		//remove brackets
		tester = tester.substring(1); 
		tester = tester.substring(0,tester.length()-1);
		
		//text()
		Pattern pat1 = Pattern.compile("(\\s)*text\\(\\)(\\s)*=(\\s)*\"[^\"]+\"(\\s)*");
		Matcher mat1 = pat1.matcher(tester); 
		if(mat1.find() && mat1.end()>=tester.length())
			return true; 
		
		//@attname
		Pattern pat2 = Pattern.compile("(\\s)*@[\\p{Alnum}\\-:_]+(\\s)*=(\\s)*\"[^\"]+\"(\\s)*");
		Matcher mat2 = pat2.matcher(tester); 
		if(mat2.find() && mat2.end()>=tester.length())
			return true;
	
		//contains
		Pattern pat3 = Pattern.compile("(\\s)*contains(\\s)*\\((\\s)*text\\(\\)(\\s)*\\,(\\s)*\"[^\"]+\"(\\s)*\\)(\\s)*"); 
		Matcher mat3 = pat3.matcher(tester); 
		if(mat3.find() && mat3.end()>=tester.length())
			return true;
		
		//recursive case if other tests failed
		if(isStep(tester))
			return true;
		
		//default to failure 
		return false; 
	}
}
