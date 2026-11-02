import os, sys, unittest
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from protocol import *

class ModbusProtocolTests(unittest.TestCase):
    def test_ecblue_defaults(self):
        self.assertEqual(encode_h3(247, 19200, '8E1'), 0xF722)
        self.assertEqual(decode_h3(0xF722), ComSetting(247, 19200, '8E1'))

    def test_target_presets(self):
        self.assertEqual(PRESETS['NAWIEW'], 0xE713)
        self.assertEqual(PRESETS['WYWIEW'], 0xE813)
        self.assertEqual(decode_h3(PRESETS['NAWIEW']), ComSetting(231,9600,'8N2'))
        self.assertEqual(decode_h3(PRESETS['WYWIEW']), ComSetting(232,9600,'8N2'))

    def test_crc_and_frame(self):
        req=read_request(247,3)
        self.assertEqual(req[:6], bytes([247,3,0,3,0,1]))
        self.assertEqual(crc16(req[:-2]).to_bytes(2,'little'), req[-2:])
        self.assertEqual(write_request(247,3,PRESETS['NAWIEW'])[:6], bytes([247,6,0,3,0xe7,0x13]))

    def test_parse_read(self):
        payload=bytes([247,3,2,0xF7,0x22]); reply=payload+crc16(payload).to_bytes(2,'little')
        self.assertEqual(check_reply(reply, 247,3,3,1),0xF722)

    def test_write_ack(self):
        f=write_request(247,3,PRESETS['NAWIEW'])
        self.assertEqual(check_reply(f,247,6,3,PRESETS['NAWIEW']),PRESETS['NAWIEW'])
        with self.assertRaises(ModbusError):
            check_reply(f,247,6,3,PRESETS['WYWIEW'])

    def test_crc_rejects(self):
        p=bytearray(read_request(247))
        p[3]=9
        with self.assertRaises(ModbusError):
            check_reply(bytes(p),247,6,3,1)

    def test_exception(self):
        p=bytes([247,0x83,2]);response=p+crc16(p).to_bytes(2,'little')
        with self.assertRaises(ModbusException):
            check_reply(response,247,3,3,1)

    def test_invalid_config(self):
        with self.assertRaises(ModbusError):
            decode_h3(0xFF15)
        with self.assertRaises(ValueError):
            encode_h3(248)

if __name__=='__main__':unittest.main()
