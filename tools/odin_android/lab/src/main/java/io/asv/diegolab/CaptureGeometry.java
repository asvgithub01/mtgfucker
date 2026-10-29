package io.asv.odinlab;
import java.util.Arrays;
final class CaptureGeometry {
 /** Convex ordered quad, long side vertical; no fabricated fallback rectangle. */
 static double[][] order(float[] q,int width,int height){
  if(q.length!=8)throw new IllegalArgumentException("No hay cuatro esquinas");double[][] p=new double[4][2];double cx=0,cy=0;
  for(int i=0;i<4;i++){float x=q[2*i],y=q[2*i+1];if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("Esquinas fuera de la foto; repite la captura");p[i]=new double[]{x,y};cx+=x/4.;cy+=y/4.;}
  final double x=cx,y=cy;Arrays.sort(p,(a,b)->Double.compare(Math.atan2(a[1]-y,a[0]-x),Math.atan2(b[1]-y,b[0]-x)));
  double area=0;for(int i=0;i<4;i++){double[] a=p[i],b=p[(i+1)%4],c=p[(i+2)%4];if((b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0])<=0)throw new IllegalArgumentException("Cuadrilátero no convexo");area+=a[0]*b[1]-b[0]*a[1];}
  if(area/2<.015)throw new IllegalArgumentException("Carta demasiado pequeña");
  int start=0;for(int i=1;i<4;i++)if(p[i][0]+p[i][1]<p[start][0]+p[start][1])start=i;
  double[] a=p[start],b=p[(start+1)%4],c=p[(start+2)%4];
  if(Math.hypot((b[0]-a[0])*width,(b[1]-a[1])*height)>Math.hypot((c[0]-b[0])*width,(c[1]-b[1])*height))start=(start+1)%4;
  double[][] ordered=new double[4][];for(int i=0;i<4;i++)ordered[i]=p[(start+i)%4];return ordered;
 }
}
