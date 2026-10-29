package io.asv.odinlab;
final class StableCrop {
 private double[][] previous;private int consecutive;
 void reset(){previous=null;consecutive=0;}
 boolean update(double[][] q){if(q==null){reset();return false;}double delta=0;if(previous!=null)for(int i=0;i<4;i++)delta+=Math.hypot(q[i][0]-previous[i][0],q[i][1]-previous[i][1])/4;consecutive=previous!=null&&delta<.015?consecutive+1:1;previous=q;return consecutive>=3;}
}
