package io.asv.odinlab;

import org.junit.Test;
import static org.junit.Assert.*;
import java.security.MessageDigest;

public class PolicyTest {
    private String golden(int w,int h,int size,boolean cubic)throws Exception {
        byte[] input=new byte[w*h*3];for(int i=0;i<input.length;i++)input[i]=(byte)(i*37+19);
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(Preprocess.resize(input,w,h,size,cubic));
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format("%02x",b&255));return out.toString();
    }
    @Test public void pillowBilinearDownsample()throws Exception {
        assertEquals("556816e8cb4d7132ee6e895eb38e59aa4c1f866f8538b44af2dc01cf49fb1ba9",golden(13,19,7,false));
    }
    @Test public void pillowBicubicDownsample()throws Exception {
        assertEquals("3a8b391c0b48f816382effb3b2465ba8e6328a00501c7d7fdd2260fe35ecd68b",golden(13,19,7,true));
    }
    @Test public void pillowBilinearUpsample()throws Exception {
        assertEquals("0a27e7b9172cbb81c8b7d525503488a0a0f928b0119ef03d1e4718acf8892c15",golden(5,9,17,false));
    }
    @Test public void pillowBicubicUpsample()throws Exception {
        assertEquals("5b30b2e78fb2df6cadfe8b9116cce067a9b84314411584cd0e4145f5178a1025",golden(5,9,17,true));
    }
    @Test public void pillowIdentity()throws Exception {
        assertEquals("5987d7812400dec04845c29540945038b07e82869effe4a0706bd595b75317d7",golden(9,9,9,true));
    }
    @Test public void rotationBeforeCrop(){
        int[] pixels={0xff100000,0xff200000,0xff300000,0xff400000};
        byte[] rgb=Preprocess.view(pixels,2,2,0,true,2,false);
        assertEquals(64,rgb[0]&255);assertEquals(16,rgb[9]&255);
    }
    @Test public void tensorCHW(){
        float[] t=Preprocess.tensor(new byte[]{(byte)255,0,0,0,(byte)255,0});
        assertEquals((1-.485f)/.229f,t[0],1e-6);assertEquals((1-.456f)/.224f,t[3],1e-6);
    }
    @Test public void diegoTensorBitExactAgainstNumpy(){
        byte[] rgb=new byte[12];for(int i=0;i<12;i++)rgb[i]=(byte)i;
        long[] expected={3221719997L,3221504518L,3221289039L,3220921647L,3221301838L,3220937627L,3220497049L,3220056470L,3219292620L,3218854000L,3218415380L,3217976759L};
        float[] t=Preprocess.diegoTensor(rgb);for(int i=0;i<t.length;i++)assertEquals(expected[i],Integer.toUnsignedLong(Float.floatToRawIntBits(t[i])));
    }
    @Test public void orientationFusionNotRegionMax(){
        float[] gallery={1,1,1,1,1};
        float[][] q={{1},{0},{1},{1},{1},{0},{1},{0},{0},{0}};
        Ranking.Result r=Ranking.rank(gallery,1,5,1,q);
        assertEquals(.6f,r.scores()[0],1e-6);assertEquals(0,r.orientations()[0]);
    }
    @Test public void tiesStableAndBestRotation(){
        Ranking.Result r=Ranking.rank(new float[]{1,0,1,0},2,1,2,new float[][]{{0,1},{1,0}});
        assertArrayEquals(new Integer[]{0,1},r.indices());assertEquals(180,r.orientations()[0]);
    }
    @Test(expected=IllegalArgumentException.class) public void zeroRejected(){Ranking.normalize(new float[3]);}
    @Test(expected=IllegalArgumentException.class) public void nanRejected(){Ranking.normalize(new float[]{Float.NaN});}
    @Test(expected=IllegalArgumentException.class) public void shapeRejected(){Ranking.rank(new float[0],2,1,2,new float[2][2]);}
}
