package org.eclipse.jdt.ui.tests.changes;


import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;


public class AllTests {
	
	public static Test suite ( ) {
		TestSuite suite= new TestSuite("All Change Tests");
		suite.addTest(TrackPositionTest.suite());
	    return suite;
	}
}


