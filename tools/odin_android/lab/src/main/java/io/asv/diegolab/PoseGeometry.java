package io.asv.odinlab;
import java.util.*;
/** Decoding contracts are deliberately separate: YOLO letterbox vs Diego hard stretch. */
final class PoseGeometry {
 static List<float[]> yolo(float[][] rows,int w,int h){double scale=Math.min(640.0/w,640.0/h);int rw=(int)Math.rint(w*scale),rh=(int)Math.rint(h*scale),left=(int)Math.rint((640-rw)/2.0-.1),top=(int)Math.rint((640-rh)/2.0-.1);List<float[]> result=new ArrayList<>();for(float[] row:rows){if(row.length!=18)throw new IllegalArgumentException("YOLO must be [N,18]");if(!Float.isFinite(row[4])||row[4]<.25f)continue;float[] q=new float[8];for(int i=0;i<4;i++){q[2*i]=(float)((row[6+3*i]-left)/scale/w);q[2*i+1]=(float)((row[7+3*i]-top)/scale/h);}result.add(q);}return result;}
 static List<float[]> diego(float[] scores,float[][][] points){if(scores.length!=points.length)throw new IllegalArgumentException("Pose shape");int best=-1;float value=Float.NEGATIVE_INFINITY;for(int i=0;i<scores.length;i++)if(Float.isFinite(scores[i])&&scores[i]>value){best=i;value=scores[i];}List<float[]> result=new ArrayList<>();if(best<0||1/(1+Math.exp(-value))<.3)return result;float[] q=new float[8];for(int i=0;i<4;i++){q[2*i]=points[best][i][0];q[2*i+1]=points[best][i][1];}result.add(q);return result;}
}
