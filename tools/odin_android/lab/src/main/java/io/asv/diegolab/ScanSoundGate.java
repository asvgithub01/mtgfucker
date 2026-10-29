package io.asv.odinlab;
/** A stable candidate notification, NOT a declaration that the recognition is correct. */
final class ScanSoundGate {
 private String pending,last;private int repeats;private long missingSince=-1;
 void reset(){pending=null;last=null;repeats=0;missingSince=-1;}
 boolean update(String id,boolean stable,long now){
  if(id==null){pending=null;repeats=0;if(missingSince<0)missingSince=now;if(now-missingSince>=800)last=null;return false;}
  missingSince=-1;if(!id.equals(pending)){pending=id;repeats=1;}else repeats++;
  if(stable&&repeats>=3&&!id.equals(last)){last=id;return true;}return false;
 }
}
