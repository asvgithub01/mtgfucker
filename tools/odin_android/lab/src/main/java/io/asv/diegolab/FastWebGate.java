package io.asv.odinlab;
/** FastWeb presence is constant: use SimCC sharpness, then common portrait geometry. */
final class FastWebGate {
 static final double MIN_SHARPNESS=.02;
 static boolean accept(float[] corners,double sharpness){
  if(!Double.isFinite(sharpness)||sharpness<MIN_SHARPNESS||corners==null||corners.length!=8)return false;
  for(float v:corners)if(!Float.isFinite(v)||v<0||v>1)return false;
  return true;
 }
}
