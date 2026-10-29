package io.asv.odinlab;
import org.junit.Test;import static org.junit.Assert.*;
public class FastWebGateTest {
 private float[] q(){return new float[]{.1f,.1f,.8f,.1f,.8f,.9f,.1f,.9f};}
 @Test public void acceptsSharp(){assertTrue(FastWebGate.accept(q(),.03));}
 @Test public void thresholdInclusive(){assertTrue(FastWebGate.accept(q(),.02));}
 @Test public void rejectsBlank(){assertFalse(FastWebGate.accept(q(),.008));}
 @Test public void rejectsInvalidSharpness(){assertFalse(FastWebGate.accept(q(),Double.NaN));assertFalse(FastWebGate.accept(q(),Double.POSITIVE_INFINITY));}
 @Test public void rejectsMalformed(){assertFalse(FastWebGate.accept(new float[6],.1));assertFalse(FastWebGate.accept(null,.1));float[] a=q();a[0]=Float.NaN;assertFalse(FastWebGate.accept(a,.1));a[0]=1.1f;assertFalse(FastWebGate.accept(a,.1));}
}
