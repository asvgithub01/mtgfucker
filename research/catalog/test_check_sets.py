import unittest
from check_sets import compare

class DiffTest(unittest.TestCase):
    def test_diff(self):
        r=compare([dict(code='old',card_count=1),dict(code='gone')],[dict(code='old',card_count=2),dict(code='new')])
        self.assertEqual(r['added'][0]['code'],'new');self.assertEqual(len(r['changed']),1);self.assertEqual(r['removedCodes'],['gone'])

if __name__=='__main__':unittest.main()
