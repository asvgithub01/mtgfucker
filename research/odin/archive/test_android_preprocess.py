import json,subprocess
from pathlib import Path
import numpy as np
from PIL import Image
R=Path(r'C:/asv/datasets/mtg-identity-v1');O=R/'android-preprocess-parity-01';O.mkdir(exist_ok=True)
W=Path(r'C:/Users/nubor/.codex/worktrees/edscan-fastweb-diego/mtgfucker');src=W/'collectorvision-native/src/main/java/io/asv/collectorvision/OwnIdentityPreprocess.java'
h=O/'ResizeHarness.java';h.write_text('''import java.nio.file.*;import io.asv.collectorvision.OwnIdentityPreprocess; public class ResizeHarness{public static void main(String[]a)throws Exception{byte[] r=Files.readAllBytes(Path.of(a[0]));Files.write(Path.of(a[3]),OwnIdentityPreprocess.resize(r,Integer.parseInt(a[1]),Integer.parseInt(a[2]),320,448,false));}}''')
j=Path(r'C:/Program Files/Java/jdk-19/bin');subprocess.run([str(j/'javac.exe'),'--release','17','-d',str(O),str(src),str(h)],check=True)
results=[]
for w,ht in [(640,896),(320,448),(499,703),(240,330),(1,1)]:
 a=np.random.default_rng(w).integers(0,256,(ht,w,3),dtype=np.uint8);i=O/f'{w}x{ht}.rgb';o=O/f'{w}x{ht}-java.rgb';i.write_bytes(a.tobytes());subprocess.run([str(j/'java.exe'),'-cp',str(O),'ResizeHarness',str(i),str(w),str(ht),str(o)],check=True)
 ref=np.array(Image.fromarray(a).resize((320,448),Image.Resampling.BILINEAR));actual=np.frombuffer(o.read_bytes(),np.uint8).reshape(448,320,3);diff=np.abs(actual.astype(int)-ref.astype(int));results.append({'width':w,'height':ht,'max_abs':int(diff.max()),'different_channels':int((diff!=0).sum())});assert diff.max()==0
(O/'results.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))
