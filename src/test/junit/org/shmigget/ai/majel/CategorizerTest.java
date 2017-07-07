/**
 * 
 */
package org.shmigget.ai.majel;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * @author C
 *
 */
public class CategorizerTest {

	/**
	 * Test method for {@link org.shmigget.ai.majel.Categorizer#categorizeInput(java.lang.String, java.lang.String)}.
	 */
	@Test
	public void testCategorizeInput() {
//		fail("Not yet implemented");
	}
	
	@Test
	public void testIsInputAllTerm() {
		// The method under test is private, so I'm going to use a bit of reflection
		// to make it accessible for testing
		Class<?> categorizerClass;
		try {
			categorizerClass = Class.forName("org.shmigget.ai.majel.Categorizer");
			Method methodUnderTest = categorizerClass.getDeclaredMethod("isInputAllTerm", 
					String.class);
			methodUnderTest.setAccessible(true);
			// Ok, now that the method is accessible, let's do a positive test
			Categorizer categorizer = new Categorizer();
			String testInput = new String("\"test\"");
			assertEquals(true, methodUnderTest.invoke(categorizer, testInput));
			// Now let's do a negative test
			String negativeTestInput = new String("");
			assertEquals(false, methodUnderTest.invoke(categorizer, negativeTestInput));
		} 
		catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
