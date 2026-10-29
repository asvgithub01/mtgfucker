package io.asv.odinlab;
final class CvQuadPolicy {
 static boolean accept(double[][] q,int w,int h){double area=0;for(int i=0;i<4;i++){double[] a=q[i],b=q[(i+1)%4];if(a[0]*w<2||a[1]*h<2||a[0]*w>w-3||a[1]*h>h-3)return false;area+=a[0]*b[1]-b[0]*a[1];}area/=2;double ratio=(UprightQuad.length(q[0],q[1],w,h)+UprightQuad.length(q[2],q[3],w,h))/(UprightQuad.length(q[1],q[2],w,h)+UprightQuad.length(q[3],q[0],w,h));return area>=.02&&area<=.95&&ratio>=.5&&ratio<=.9;}
}
