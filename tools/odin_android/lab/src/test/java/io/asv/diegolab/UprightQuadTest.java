package io.asv.odinlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class UprightQuadTest {
 private final float[] portrait={.8f,.9f,.2f,.1f,.2f,.9f,.8f,.1f};
 @Test public void ordersWithoutRotating(){double[][] q=UprightQuad.order(portrait,100,140);assertEquals(.2,q[0][0],1e-6);assertEquals(.1,q[0][1],1e-6);assertEquals(.8,q[1][0],1e-6);assertEquals(.9,q[2][1],1e-6);}
 @Test(expected=IllegalArgumentException.class) public void rejectsLandscape(){UprightQuad.order(portrait,200,100);}
 @Test(expected=IllegalArgumentException.class) public void rejectsNaN(){UprightQuad.order(new float[]{Float.NaN,0,1,0,1,1,0,1},100,140);}
 @Test(expected=IllegalArgumentException.class) public void rejectsDuplicate(){UprightQuad.order(new float[]{0,0,0,0,1,1,0,1},100,140);}
 @Test public void stabilityNeedsThreeAndResetsOnLoss(){StableCrop s=new StableCrop();double[][] q=UprightQuad.order(portrait,100,140);assertFalse(s.update(q));assertFalse(s.update(q));assertTrue(s.update(q));assertFalse(s.update(null));assertFalse(s.update(q));}
 @Test public void movementResetsStability(){StableCrop s=new StableCrop();double[][] q=UprightQuad.order(portrait,100,140);s.update(q);s.update(q);double[][] moved=new double[4][2];for(int i=0;i<4;i++)moved[i]=new double[]{q[i][0]+.03,q[i][1]};assertFalse(s.update(moved));assertFalse(s.update(moved));assertTrue(s.update(moved));}
}
