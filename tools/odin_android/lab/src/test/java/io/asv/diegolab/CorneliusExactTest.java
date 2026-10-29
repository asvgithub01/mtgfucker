package io.asv.odinlab;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class CorneliusExactTest {
 @Test public void bitExactAcrossDimensionsAndColors(){Random rng=new Random(947);for(int[] wh:new int[][]{{1,1},{2,7},{31,19},{384,384},{640,896},{960,1280},{2160,2160},{2048,1537}}){int[] a=new int[wh[0]*wh[1]];for(int i=0;i<a.length;i++)a[i]=rng.nextInt();assertArrayEquals(Preprocess.view(a,wh[0],wh[1],0,false,384,false),Preprocess.corneliusExact(a,wh[0],wh[1],384));}}
 @Test public void solidColors(){for(int c:new int[]{0,0xffffffff,0xff0000ff,0xff00ff00,0xffff0000}){int[] a=new int[971*17];java.util.Arrays.fill(a,c);assertArrayEquals(Preprocess.view(a,971,17,0,false,384,false),Preprocess.corneliusExact(a,971,17,384));}}
 @Test(expected=IllegalArgumentException.class)public void rejectsWrongLength(){Preprocess.corneliusExact(new int[3],2,2,384);}
}
