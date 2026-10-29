package io.asv.odinlab;
import java.util.*;
/** Ultralytics detect [5,N], xywh+score, class-agnostic NMS. Rectangles, NOT pose. */
final class RiftGeometry {
 static List<float[]> decode(float[][] values,int w,int h){
  if(values.length!=5||w<=0||h<=0)throw new IllegalArgumentException("Rift shape");int n=values[0].length;for(float[] row:values)if(row.length!=n)throw new IllegalArgumentException("Rift shape");
  List<float[]> boxes=new ArrayList<>();
  for(int i=0;i<n;i++){float cx=values[0][i],cy=values[1][i],bw=values[2][i],bh=values[3][i],score=values[4][i];if(!Float.isFinite(cx)||!Float.isFinite(cy)||!Float.isFinite(bw)||!Float.isFinite(bh)||!Float.isFinite(score)||score<.25f||bw<=0||bh<=0)continue;boxes.add(new float[]{cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2,score});}
  boxes.sort((a,b)->Float.compare(b[4],a[4]));List<float[]> kept=new ArrayList<>();
  for(float[] box:boxes){boolean suppressed=false;for(float[] other:kept)if(iou(box,other)>.45){suppressed=true;break;}if(!suppressed){kept.add(box);if(kept.size()>=300)break;}}
  double scale=Math.min(640.0/w,640.0/h);int rw=(int)Math.rint(w*scale),rh=(int)Math.rint(h*scale),left=(int)Math.rint((640-rw)/2.0-.1),top=(int)Math.rint((640-rh)/2.0-.1);List<float[]> out=new ArrayList<>();
  for(float[] box:kept){float l=(float)Math.max(0,Math.min(w,(box[0]-left)/scale)),t=(float)Math.max(0,Math.min(h,(box[1]-top)/scale)),r=(float)Math.max(0,Math.min(w,(box[2]-left)/scale)),b=(float)Math.max(0,Math.min(h,(box[3]-top)/scale));if(r-l<=1||b-t<=1)continue;out.add(new float[]{l/w,t/h,r/w,t/h,r/w,b/h,l/w,b/h});}return out;
 }
 static double iou(float[] a,float[] b){double inter=Math.max(0,Math.min(a[2],b[2])-Math.max(a[0],b[0]))*Math.max(0,Math.min(a[3],b[3])-Math.max(a[1],b[1]));double union=(a[2]-a[0])*(a[3]-a[1])+(b[2]-b[0])*(b[3]-b[1])-inter;return union<=0?0:inter/union;}
}
