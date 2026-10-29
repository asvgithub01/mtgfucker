package io.asv.odinlab;
import org.junit.Test;import static org.junit.Assert.*;
public class ScanSoundGateTest {
 @Test public void onceAfterThree(){ScanSoundGate g=new ScanSoundGate();assertFalse(g.update("a",true,0));assertFalse(g.update("a",true,100));assertTrue(g.update("a",true,200));assertFalse(g.update("a",true,300));}
 @Test public void geometryMustBeStable(){ScanSoundGate g=new ScanSoundGate();for(int i=0;i<5;i++)assertFalse(g.update("a",false,i));assertTrue(g.update("a",true,10));}
 @Test public void shortLossDoesNotRearm(){ScanSoundGate g=new ScanSoundGate();for(int i=0;i<3;i++)g.update("a",true,i);g.update(null,false,100);g.update(null,false,200);for(int i=0;i<4;i++)assertFalse(g.update("a",true,300+i));}
 @Test public void removalRearms(){ScanSoundGate g=new ScanSoundGate();for(int i=0;i<3;i++)g.update("a",true,i);g.update(null,false,100);g.update(null,false,901);g.update("a",true,1000);g.update("a",true,1100);assertTrue(g.update("a",true,1200));}
 @Test public void newCardAndReset(){ScanSoundGate g=new ScanSoundGate();for(int i=0;i<3;i++)g.update("a",true,i);g.update("b",true,10);g.update("b",true,20);assertTrue(g.update("b",true,30));g.reset();g.update("b",true,40);g.update("b",true,50);assertTrue(g.update("b",true,60));}
}
