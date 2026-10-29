package io.asv.odinlab;
import org.junit.Test;import static org.junit.Assert.*;
public class CvQuadPolicyTest {
 private double[][] rect(double x,double y,double w,double h){return new double[][]{{x,y},{x+w,y},{x+w,y+h},{x,y+h}};}
 @Test public void acceptsCard(){assertTrue(CvQuadPolicy.accept(rect(.2,.2,.6,.6),1000,1400));}
 @Test public void rejectsFrame(){assertFalse(CvQuadPolicy.accept(rect(0,0,1,1),1000,1400));}
 @Test public void rejectsTiny(){assertFalse(CvQuadPolicy.accept(rect(.3,.3,.05,.05),1000,1400));}
 @Test public void rejectsWide(){assertFalse(CvQuadPolicy.accept(rect(.2,.2,.6,.3),1000,1400));}
 @Test public void rejectsNarrow(){assertFalse(CvQuadPolicy.accept(rect(.2,.2,.1,.6),1000,1400));}
}
