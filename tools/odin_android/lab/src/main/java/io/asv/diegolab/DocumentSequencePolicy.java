package io.asv.odinlab;
import java.util.*;
final class DocumentSequencePolicy {
 private final Set<String> delivered=new HashSet<>();
 boolean notifyCandidate(String event,boolean saved,boolean hasCandidate){return saved&&hasCandidate&&event!=null&&delivered.add(event);}
 static int sampleSize(int width,int height){if(width<=0||height<=0)throw new IllegalArgumentException("Invalid image dimensions");int n=1;while(Math.max(width,height)/n>2048)n*=2;return n;}
}
