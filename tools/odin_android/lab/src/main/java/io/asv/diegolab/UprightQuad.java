package io.asv.odinlab;
import java.util.*;
/** Screen top stays card top. Never rotate a card to try another orientation. */
final class UprightQuad {
 static double[][] order(float[] q,int w,int h){
  if(q.length!=8)throw new IllegalArgumentException("Four corners required");double[][] p=new double[4][2];double cx=0,cy=0;
  for(int i=0;i<4;i++){double x=q[i*2],y=q[i*2+1];if(!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("Corners outside frame");p[i]=new double[]{x,y};cx+=x/4;cy+=y/4;}
  final double x=cx,y=cy;Arrays.sort(p,Comparator.comparingDouble(a->Math.atan2((a[1]-y)*h,(a[0]-x)*w)));int start=0;for(int i=1;i<4;i++)if(p[i][0]*w+p[i][1]*h<p[start][0]*w+p[start][1]*h)start=i;
  double[][] a=new double[4][];for(int i=0;i<4;i++)a[i]=p[(i+start)%4];double area=0;
  for(int i=0;i<4;i++){double[] b=a[i],c=a[(i+1)%4],d=a[(i+2)%4];double cross=(c[0]-b[0])*(d[1]-c[1])-(c[1]-b[1])*(d[0]-c[0]);if(cross<=0)throw new IllegalArgumentException("Not convex");area+=b[0]*c[1]-c[0]*b[1];}
  if(area/2<.002)throw new IllegalArgumentException("Too small");double horizontal=length(a[0],a[1],w,h)+length(a[2],a[3],w,h),vertical=length(a[1],a[2],w,h)+length(a[3],a[0],w,h);
  if(vertical<=horizontal)throw new IllegalArgumentException("Only portrait cards, title at top");return a;
 }
 static double length(double[] a,double[] b,int w,int h){return Math.hypot((a[0]-b[0])*w,(a[1]-b[1])*h);}
 static double centerDistance(double[][] q,float x,float y){double cx=0,cy=0;for(double[] p:q){cx+=p[0]/4;cy+=p[1]/4;}return Math.hypot(cx-x,cy-y);}
}
