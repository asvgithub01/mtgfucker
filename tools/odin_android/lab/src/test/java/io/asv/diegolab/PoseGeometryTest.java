package io.asv.odinlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class PoseGeometryTest {
 @Test public void yoloUndoesLetterbox(){float[] row=new float[18];row[4]=.9f;float[][] p={{160,0},{480,0},{480,640},{160,640}};for(int i=0;i<4;i++){row[6+3*i]=p[i][0];row[7+3*i]=p[i][1];}float[] q=PoseGeometry.yolo(new float[][]{row},320,640).get(0);assertArrayEquals(new float[]{0,0,1,0,1,1,0,1},q,1e-6f);}
 @Test public void yoloRejectsLowScore(){assertTrue(PoseGeometry.yolo(new float[1][18],640,640).isEmpty());}
 @Test public void diegoSelectsOnlyHighestQuery(){float[][][] p={{{0,0},{1,0},{1,1},{0,1}},{{.1f,.2f},{.8f,.2f},{.8f,.9f},{.1f,.9f}}};assertEquals(.1f,PoseGeometry.diego(new float[]{0,2},p).get(0)[0],1e-6f);assertEquals(1,PoseGeometry.diego(new float[]{0,2},p).size());}
 @Test public void diegoAppliesSigmoidThreshold(){assertTrue(PoseGeometry.diego(new float[]{-10},new float[1][4][2]).isEmpty());assertEquals(1,PoseGeometry.diego(new float[]{0},new float[1][4][2]).size());}
 @Test(expected=IllegalArgumentException.class)public void rejectWrongYoloContract(){PoseGeometry.yolo(new float[1][17],640,640);}
}
