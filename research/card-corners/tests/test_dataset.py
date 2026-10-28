import hashlib,json,sys,tempfile,unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from prepare import prepare
from validate import validate

class DatasetTests(unittest.TestCase):
    def fixture(self,root):
        rows=[]
        for i in range(2):
            p=root/f'{i:06}.png';Image.new('RGB',(64,96),(i*200,0,0)).save(p)
            sha=hashlib.sha256(p.read_bytes()).hexdigest()
            r=dict(schema=1,coordinates='normalized-top-left',cornerOrder='texture-TL,TR,BR,BL',image=p.name,imageSha256=sha,width=64,height=96,group=str(i),textureSha256=sha,split=('train','validation')[i],present=True,corners=[dict(x=x,y=y) for x,y in [(0.1,.1),(.9,.1),(.9,.9),(.1,.9)]])
            (root/f'{i:06}.json').write_text(json.dumps(r));rows.append(r)
        (root/'COMPLETE').write_text('2');return rows
    def test_valid(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);self.fixture(r);self.assertEqual(len(validate(r)),2)
    def test_incomplete(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(FileNotFoundError):validate(t)
    def test_hidden_corner_retained(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);rows=self.fixture(root);r=rows[0]
            r.update(schema=2,cornerVisible=[False,True,True,True],thumbOccluder=True)
            (root/'000000.json').write_text(json.dumps(r))
            result=validate(root)
            self.assertEqual(len(result[0]['corners']),4)
            self.assertFalse(result[0]['cornerVisible'][0])
    def test_invalid_visibility(self):
        with tempfile.TemporaryDirectory() as t:
            root=Path(t);rows=self.fixture(root);r=rows[0]
            r.update(schema=2,cornerVisible=[False],thumbOccluder=True)
            (root/'000000.json').write_text(json.dumps(r))
            with self.assertRaises(AssertionError):validate(root)
    def test_leak(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);rows=self.fixture(r);rows[1]['group']='0';(r/'000001.json').write_text(json.dumps(rows[1]))
            with self.assertRaises(AssertionError):validate(r)
    def test_hash(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);self.fixture(r);(r/'000001.png').write_bytes(b'bad')
            with self.assertRaises(AssertionError):validate(r)
    def test_winding(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);rows=self.fixture(r);rows[0]['corners'].reverse();(r/'000000.json').write_text(json.dumps(rows[0]))
            with self.assertRaises(AssertionError):validate(r)
    def test_config_duplicate_hash(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);self.fixture(r)
            with self.assertRaises(ValueError):prepare([dict(path=str(r/'000000.png'),group='a',split='train'),dict(path=str(r/'000000.png'),group='b',split='validation')],r/'output')
    def test_config(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);self.fixture(r)
            cfg=prepare([dict(path=str(r/f'{i:06}.png'),group=str(i),split=s) for i,s in enumerate(('train','validation'))],r/'out')
            self.assertEqual(cfg['count'],120)

if __name__=='__main__':unittest.main()
