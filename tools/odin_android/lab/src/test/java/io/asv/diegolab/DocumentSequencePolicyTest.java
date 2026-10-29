package io.asv.odinlab;
import org.junit.Test;import static org.junit.Assert.*;
public class DocumentSequencePolicyTest {
 @Test public void noDuplicateNotification(){DocumentSequencePolicy p=new DocumentSequencePolicy();assertTrue(p.notifyCandidate("a",true,true));assertFalse(p.notifyCandidate("a",true,true));assertTrue(p.notifyCandidate("b",true,true));}
 @Test public void errorsDoNotNotify(){DocumentSequencePolicy p=new DocumentSequencePolicy();assertFalse(p.notifyCandidate("a",false,true));assertFalse(p.notifyCandidate("a",true,false));assertTrue(p.notifyCandidate("a",true,true));}
 @Test public void decodeBound(){assertEquals(1,DocumentSequencePolicy.sampleSize(640,896));assertEquals(2,DocumentSequencePolicy.sampleSize(4000,3000));assertEquals(4,DocumentSequencePolicy.sampleSize(8000,6000));}
 @Test(expected=IllegalArgumentException.class) public void rejectBadImage(){DocumentSequencePolicy.sampleSize(-1,100);}
}
