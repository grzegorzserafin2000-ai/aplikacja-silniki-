import os, sys, unittest
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from protocol import encode_h3, decode_h3, ModbusError
from serial_service import _settings_groups, _priority_addresses

class ManualConfigTests(unittest.TestCase):
    def test_manual_supported_modes(self):
        for address in (1, 231, 232, 247):
            for baud in (4800, 9600, 19200, 38400, 115200):
                for mode in ('8N1', '8O1', '8E1', '8N2'):
                    value = encode_h3(address,baud,mode)
                    setting = decode_h3(value)
                    self.assertEqual((setting.address,setting.baud,setting.mode), (address,baud,mode))

    def test_manual_bad_combinations(self):
        for v in ('8E2','8O2','8N3'):
            with self.assertRaises(ValueError):
                encode_h3(231,9600,v)
        for adr in (-1, 0, 248, 350):
            with self.assertRaises(ValueError):
                encode_h3(adr,9600,'8N2')

    def test_target_is_first_for_verification(self):
        target = encode_h3(42, 38400, '8E1')
        self.assertEqual(_priority_addresses(target)[0], 42)
        self.assertEqual(_settings_groups(target)[0], (38400, 'E', 1))

if __name__ == '__main__': unittest.main()
