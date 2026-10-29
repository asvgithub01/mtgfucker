package io.asv.odinlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class CaptureGeometryTest {
 @Test public void unorderedPortrait(){double[][] q=CaptureGeometry.order(new float[]{.9f,.9f,.1f,.1f,.1f,.9f,.9f,.1f},100,200);assertEquals(.1,q[0][0],1e-6);assertEquals(.1,q[0][1],1e-6);assertEquals(.9,q[1][0],1e-6);}
 @Test public void landscapeTurnsLongEdgeVertical(){double[][] q=CaptureGeometry.order(new float[]{.1f,.1f,.9f,.1f,.9f,.9f,.1f,.9f},200,100);assertEquals(q[0][0],q[1][0],1e-6);}
 @Test(expected=IllegalArgumentException.class) public void rejectsNaN(){CaptureGeometry.order(new float[]{Float.NaN,0,1,0,1,1,0,1},100,200);}
 @Test(expected=IllegalArgumentException.class) public void rejectsOutside(){CaptureGeometry.order(new float[]{-.1f,0,1,0,1,1,0,1},100,200);}
 @Test(expected=IllegalArgumentException.class) public void rejectsDuplicate(){CaptureGeometry.order(new float[]{0,0,0,0,1,1,0,1},100,200);}
 @Test(expected=IllegalArgumentException.class) public void rejectsTiny(){CaptureGeometry.order(new float[]{0,0,.01f,0,.01f,.01f,0,.01f},100,200);}
}
