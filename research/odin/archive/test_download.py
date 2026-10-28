import copy, unittest
from download import faces

class MetadataTests(unittest.TestCase):
    def setUp(self):
        self.card = dict(id='12345678-1234-1234-1234-123456789abc', set='spg', set_name='Special Guests', lang='en', games=['paper'], layout='normal', image_status='highres_scan', name='Example', collector_number='1', oracle_id='oracle', illustration_id='art', image_uris={'large':'https://cards.scryfall.io/large/example.jpg'})
    def test_full_card_keeps_identity(self):
        row = list(faces(self.card))[0]
        self.assertEqual(row['illustration_id'], 'art')
        self.assertTrue(row['path'].startswith('images/spg/'))
    def test_two_faces(self):
        self.card.pop('image_uris')
        self.card['card_faces'] = [{'name':str(i), 'illustration_id':str(i), 'image_uris':{'large':'https://cards.scryfall.io/example.jpg'}} for i in range(2)]
        rows = list(faces(self.card))
        self.assertEqual([x['face'] for x in rows], [0,1])
        self.assertNotEqual(rows[0]['id'], rows[1]['id'])
    def test_english_only(self):
        self.card['lang'] = 'es'
        self.assertEqual(list(faces(self.card)), [])
    def test_exclude_digital_and_placeholders(self):
        for changes in [{'games':['arena']}, {'image_status':'placeholder'}, {'oversized':True}, {'layout':'token'}]:
            card = copy.deepcopy(self.card); card.update(changes)
            self.assertEqual(list(faces(card)), [])
    def test_path_validation(self):
        self.card['set'] = '../escape'
        with self.assertRaises(ValueError):
            list(faces(self.card))

if __name__ == '__main__':
    unittest.main()
