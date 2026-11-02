import sys, unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from serial_service import assess_write, apply_preset
from protocol import encode_h3, ModbusException
from scan_service import Detection
from protocol import decode_h3

OLD=encode_h3(231,9600,'8N2')
TARGET=encode_h3(230,9600,'8N2')

def mk_detection():
    return Detection('COM3',231,9600,'N',2,OLD,decode_h3(OLD),3,3,0,0x100,1331)

class MockSerial:
    state=None
    def __init__(self, **kwargs):
        self.params=kwargs
        self.state.handles.append(kwargs)
    def __enter__(self): return self
    def __exit__(self,*exc): return None

class Scenario:
    def __init__(self,kind):
        self.kind=kind; self.calls=[]; self.handles=[]; self.write_calls=0
        self.raw=OLD
    def transact(self,conn,address,fn,register,value,timeout):
        self.calls.append((address,fn,register,value))
        if fn==4: return 0x100
        if fn==6 and register==0: return 1234
        if fn==6 and register==3:
            self.write_calls+=1
            if self.kind!='unchanged' and self.kind!='reject': self.raw=TARGET
            if self.kind=='reject': raise ModbusException(3)
            if self.kind != 'ack': raise TimeoutError('FC06 ack nie dotarło')
            return TARGET
        if fn==3 and register==3:
            if self.write_calls==0: return OLD
            if self.kind in ('ack','noack_old','unchanged','reject'):
                if address==231:return self.raw
                raise TimeoutError('brak odpowiedzi')
            if self.kind=='noack_new':
                if address==230: return self.raw
                raise TimeoutError('stary adres nieaktywny')
            raise TimeoutError('brak odpowiedzi na obu')
        raise AssertionError((address,fn,register,value))

class WriteAssessmentTests(unittest.TestCase):
    def test_classification(self):
        self.assertEqual(assess_write(OLD,TARGET,TARGET,None),'stored_old')
        self.assertEqual(assess_write(OLD,TARGET,None,TARGET),'stored_new')
        self.assertEqual(assess_write(OLD,TARGET,OLD,None),'unchanged')
        self.assertEqual(assess_write(OLD,TARGET,None,None),'uncertain')
        self.assertEqual(assess_write(OLD,TARGET,OLD,None,rejected=True),'rejected')
        self.assertEqual(assess_write(OLD,TARGET,TARGET,None,rejected=True),'uncertain')
    def run_kind(self,kind,expected):
        s=Scenario(kind)
        MockSerial.state=s
        with patch.dict(sys.modules,{'serial': SimpleNamespace(Serial=MockSerial)}),\
             patch('serial_service.transact',side_effect=s.transact):
            result=apply_preset(mk_detection(),TARGET,lambda _:None)
        self.assertEqual(result.status,expected)
        self.assertTrue(result.attempted)
        self.assertEqual(s.write_calls,1,'FC06 h3 must be called at most once')
        self.assertIn('FC06:',result.details)
        return result
    def test_successful_echo_old_readback(self):
        r=self.run_kind('ack','stored_old')
        self.assertEqual(r.readback,TARGET)
    def test_timeout_old_link_has_new_h3(self):
        r=self.run_kind('noack_old','stored_old')
        self.assertIn('Brak jednoznacznego potwierdzenia',r.details)
    def test_timeout_new_link_has_new_h3(self):
        self.run_kind('noack_new','stored_new')
    def test_timeout_previous_value_unchanged(self):
        self.run_kind('unchanged','unchanged')
    def test_both_links_inconclusive(self):
        self.run_kind('noanswer','uncertain')
    def test_explicit_exception(self):
        self.run_kind('reject','rejected')
    def test_noop_does_not_send(self):
        s=Scenario('ack');MockSerial.state=s
        with patch.dict(sys.modules,{'serial':SimpleNamespace(Serial=MockSerial)}),patch('serial_service.transact',side_effect=s.transact):
            result=apply_preset(mk_detection(),OLD,lambda _:None)
        self.assertFalse(result.attempted)
        self.assertEqual(s.write_calls,0)
        self.assertEqual(result.status,'already')

if __name__=='__main__':unittest.main()
