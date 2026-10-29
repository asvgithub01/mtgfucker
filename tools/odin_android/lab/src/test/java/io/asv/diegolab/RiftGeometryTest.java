package io.asv.odinlab;
import org.junit.Test;import static org.junit.Assert.*;
public class RiftGeometryTest {
 @Test public void unletterbox(){var q=RiftGeometry.decode(new float[][]{{320},{320},{320},{320},{.9f}},640,320).get(0);assertArrayEquals(new float[]{.25f,0,.75f,0,.75f,1,.25f,1},q,.00001f);}
 @Test public void nmsDuplicates(){assertEquals(1,RiftGeometry.decode(new float[][]{{320,320},{320,320},{100,100},{200,200},{.9f,.8f}},640,640).size());}
 @Test public void distinctBoxes(){assertEquals(2,RiftGeometry.decode(new float[][]{{100,500},{100,500},{50,50},{80,80},{.9f,.8f}},640,640).size());}
 @Test public void rejectInvalid(){assertTrue(RiftGeometry.decode(new float[][]{{Float.NaN,320},{320,320},{100,100},{200,200},{.9f,.1f}},640,640).isEmpty());}
 @Test(expected=IllegalArgumentException.class)public void shape(){RiftGeometry.decode(new float[4][1],640,640);}
}
