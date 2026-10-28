"""Offline isolation checks; never connects to a device or reads collection data."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
BASE = '2d8a4adb17fba50a17f26e260775e165414f4d72'
PACKAGE = 'app/src/main/java/io/asv/mtgocr/ocrreader/'

def verify():
    originals = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASE, PACKAGE], cwd=ROOT).decode().splitlines()
    originals = [p for p in originals if Path(p).name.startswith('Triple')]
    for name in originals:
        expected = subprocess.check_output(['git', 'show', f'{BASE}:{name}'], cwd=ROOT)
        assert (ROOT/name).read_bytes().replace(b'\r\n', b'\n') == expected.replace(b'\r\n', b'\n'), name
    android = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
    activities = {a.get(android+'name'): a for a in manifest.findall('.//activity')}
    old, new = activities['.TripleScanActivity'], activities['.FastWebTripleScanActivity']
    for key in ['screenOrientation', 'theme', 'exported']:
        assert old.get(android+key) == new.get(android+key), key
    source = (ROOT/PACKAGE/'FastWebTripleScanActivity.kt').read_text(encoding='utf-8')
    assert 'FastWebDiegoEngine.prepare(' in source
    assert 'NativeCollectorVisionEngine' not in source and '.configureFast(' not in source
    assert '"fastweb_triple_scanner"' in source and '"triple_scanner"' not in source
    assert 'contourEnabled=prefs.getBoolean("edscan_contour",true)' in source
    assert 'publishContour(detection,false)' in source
    assert 'onDetected=if(contourMap!=null)' in source
    assert 'FastWebTripleCollectionSession' in source and 'FastWebTripleScanTrace' in source
    own = (ROOT/PACKAGE/'OwnTripleScanActivity.kt').read_text(encoding='utf-8')
    assert 'OwnIdentityEngine.prepare(' in own
    assert 'collectionSession' not in own and 'ScanReviewStore.write' not in own
    assert '"own_triple_scanner"' in own
    assert 'evidence.enabled=on' in own and 'isChecked=false' in own
    assert 'evidence.offer(upright,evidenceCrop,meta)' in own
    assert 'evidence.export(stream)' in own
    print(f'PASS: {len(originals)} original Triple files unchanged; separate motor/preferences/session and matching manifest.')

if __name__ == '__main__':
    verify()
