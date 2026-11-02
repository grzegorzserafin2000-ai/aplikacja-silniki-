import os,unittest
from unittest.mock import patch
from protocol import encode_h3,decode_h3
from scan_service import Detection
from simple_write import Result
@unittest.skipUnless(os.name=='nt' or os.environ.get('DISPLAY'),'GUI requires display')
class GuiTests(unittest.TestCase):
 def setUp(self):
  from simple_app import SimpleApp
  self.ports=patch('simple_app.ports',return_value=['COM3']);self.ports.start()
  self.app=SimpleApp();self.app.withdraw();self.app.update()
 def tearDown(self):self.app.destroy();self.ports.stop()
 def device(self):
  raw=encode_h3(231)
  return Detection('COM3',231,9600,'E',1,raw,decode_h3(raw),3,3,0,0x0100,1331)
 def test_edit_after_preset(self):
  self.app.apply_preset(232);self.app.manual_addr.set('230')
  self.assertEqual(decode_h3(self.app.target_word()).address,230)
  self.assertEqual(self.app.manual_frame.winfo_manager(),'pack')
 def test_bad_addresses(self):
  for value in ['0','248','abc','']:
   self.app.manual_addr.set(value)
   with self.assertRaises(ValueError):self.app.target_word()
 def test_card_distinguishes_formats(self):
  self.app.detected=self.device();self.app.describe();text=self.app.device_details.get('1.0','end')
  for expected in ['1331','231 / 9600 / Even / 1','231 / 9600 / 8N2','0xE713']:self.assertIn(expected,text)
 def test_busy_freezes_fields(self):
  self.app.busy=True;self.app.update_ui()
  self.assertEqual(str(self.app.inputs[0].cget('state')),'disabled')
  self.assertEqual(str(self.app.port.cget('state')),'disabled')
 @patch('simple_app.messagebox.showinfo')
 def test_detection_fills_target(self,_):
  self.app.events.put(('found',(self.device(),{'requests':4})));self.app.handle()
  self.assertEqual(self.app.manual_addr.get(),'231');self.assertEqual(self.app.manual_parity.get(),'Even')
  self.assertIn('1331',self.app.log)
 @patch('simple_app.messagebox.showinfo')
 def test_confirmed_result_updates_card(self,_):
  self.app.detected=self.device();self.app.target=encode_h3(232)
  self.app.events.put(('programmed',Result('ok','Potwierdzono','log',True)));self.app.handle()
  self.assertEqual(self.app.detected.active_address,232);self.assertEqual(self.app.detected.parity,'N')
 def test_copy_log(self):
  self.app.log='Raport TX E7';self.app.copy_log();self.assertEqual(self.app.clipboard_get(),self.app.log)

 def test_port_change_invalidates_detection(self):
  self.app.detected=self.device();self.app.com.set('COM4');self.app.port_changed()
  self.assertIsNone(self.app.detected)
 def test_close_during_write_keeps_window(self):
  self.app.detected=self.device();self.app.busy=True;self.app.on_close()
  self.assertTrue(self.app.winfo_exists())

 @patch('simple_app.messagebox.showinfo')
 def test_factory_button_restores_communication_fields(self,_):
  self.app.select_factory()
  target=decode_h3(self.app.target_word())
  self.assertEqual((target.address,target.baud,target.mode),(247,19200,'8E1'))
  self.assertEqual(self.app.factory_btn.winfo_manager(),'pack')
 @patch('simple_app.messagebox.showinfo')
 @patch('simple_app.program_one_click')
 def test_factory_selection_does_not_write(self,program,_):
  self.app.select_factory();program.assert_not_called()
 @patch('simple_app.messagebox.showinfo')
 def test_factory_is_blocked_while_busy(self,_):
  self.app.busy=True;before=self.app.target_word();self.app.select_factory()
  self.assertEqual(self.app.target_word(),before)
  self.app.update_ui();self.assertEqual(str(self.app.factory_btn.cget('state')),'disabled')
 def test_card_cm_readout_and_target(self):
  from dataclasses import replace
  self.app.detected=replace(self.device(),control_word=0x5200);self.app.describe()
  self.assertIn('CM (odczyt / cel): 0 / 3',self.app.device_details.get('1.0','end'))
 @patch('simple_app.messagebox.showinfo')
 def test_cm_confirmed_result_shows_three_three(self,_):
  self.app.detected=self.device();self.app.target=encode_h3(232)
  self.app.events.put(('programmed',Result('ok','CM 3/3','log',True,0x5203)));self.app.handle()
  self.assertEqual(self.app.detected.control_word,0x5203)
  self.assertIn('CM (odczyt / cel): 3 / 3',self.app.device_details.get('1.0','end'))
 @patch('simple_app.messagebox.showinfo')
 def test_cm_failure_is_not_shown_as_success(self,_):
  self.app.detected=self.device();self.app.target=encode_h3(232)
  self.app.events.put(('programmed',Result('incomplete','CM niepotwierdzone','log',True)));self.app.handle()
  self.assertIsNone(self.app.detected);self.assertIn('niepotwierdzona',self.app.device.get())

 def test_original_product_name_and_full_logo(self):
  self.assertEqual(self.app.title(),'Clima Gold Ziehl-Abegg')
  self.assertEqual((self.app.icon.width(),self.app.icon.height()),(400,60))
