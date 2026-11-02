"""Simulated test of activating a previously stored communication change.
No real serial ports are opened and no motor is driven.
"""
import unittest
from unittest.mock import patch
from scan_service import Detection
from protocol import encode_h3, decode_h3
import serial_service as service

class FakePort:
    def __init__(self, state, **settings):
        self.state=state
        self.settings=settings
    def __enter__(self): return self
    def __exit__(self,*args):return False

class ActivationTests(unittest.TestCase):
    def detection(self, saved=230):
        h3=encode_h3(saved, 9600, '8N2')
        return Detection('COM3',231,9600,'N',2,h3,decode_h3(h3),3,3,0,0x100,1331)

    def test_only_3698_written_once_and_new_link_is_checked(self):
        state={'activated':False,'writes':[]}
        def port(**settings):return FakePort(state,**settings)
        def transact(conn,addr,fn,reg,val,timeout=0):
            if fn==4 and reg==1:return 0x100
            if fn==3 and reg==3:
                if addr==231 and not state['activated']:return encode_h3(230)
                if addr==230 and state['activated']:return encode_h3(230)
                raise TimeoutError('nie odpowiada')
            if fn==6:
                state['writes'].append((addr,reg,val))
                if (addr,reg,val)==(231,0,3698):state['activated']=True
                return 3698
            raise AssertionError((addr,fn,reg,val))
        with patch.dict('sys.modules',{'serial':type('SerialModule',(),{'Serial':staticmethod(port)})}), \
             patch.object(service,'transact',transact), patch.object(service.time,'sleep',lambda _:None):
            result=service.activate_stored_communication(self.detection(),lambda x:None)
        self.assertEqual(result.status,'activated')
        self.assertEqual(state['writes'],[(231,0,3698)])

    def test_matching_active_and_saved_does_not_write(self):
        with self.assertRaises(service.ModbusError):
            service.activate_stored_communication(self.detection(231),lambda x:None)

    def test_changed_h3_aborts_before_command(self):
        state={'writes':[]}
        def port(**settings):return FakePort(state,**settings)
        def transact(conn,addr,fn,reg,val,timeout=0):
            if fn==4 and reg==1:return 0x100
            if fn==3 and reg==3:return encode_h3(229)
            if fn==6:state['writes'].append((addr,reg,val))
        with patch.dict('sys.modules',{'serial':type('SerialModule',(),{'Serial':staticmethod(port)})}), \
             patch.object(service,'transact',transact):
            with self.assertRaises(service.ModbusError):
                service.activate_stored_communication(self.detection(),lambda x:None)
        self.assertEqual(state['writes'],[])

if __name__=='__main__':unittest.main()
