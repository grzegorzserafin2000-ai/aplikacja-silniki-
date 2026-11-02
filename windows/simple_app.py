"""Clima Gold minimalist Windows front end; advanced settings collapsed."""
import os, queue, threading, tkinter as tk
from tkinter import ttk, messagebox, filedialog
from dataclasses import replace
from resources import resource_path
from pathlib import Path
from protocol import encode_h3, decode_h3
from scan_service import auto_scan,manual_scan,validate_range
from serial_service import ports
from simple_write import program_one_click,eligible

BG='#000000'; PANEL='#161616'; ORANGE='#F46A17'; FG='#F9F9FA'; MUTED='#C6D0DA'

class SimpleApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title('Clima Gold Ziehl-Abegg')
        self.geometry(f'740x{min(840,self.winfo_screenheight()-100)}');self.minsize(620,560)
        self.configure(bg=BG)
        try:
            self.iconbitmap(str(resource_path('assets/climagold_dark.ico')))
            self._window_icon=tk.PhotoImage(file=str(resource_path('assets/climagold_mark.png')))
            self.iconphoto(True,self._window_icon)
        except tk.TclError:pass
        self.events=queue.Queue();self.stop=threading.Event();self.busy=False;self.detected=None
        self.com=tk.StringVar();self.choice=tk.StringVar(value='NAWIEW  •  231')
        self.status=tk.StringVar(value='Podłącz jeden silnik i kliknij WYKRYJ SILNIK')
        self.device=tk.StringVar(value='Nie wykryto silnika')
        self.manual_addr=tk.StringVar(value='231');self.manual_baud=tk.StringVar(value='9600')
        self.manual_parity=tk.StringVar(value='None');self.manual_stop=tk.StringVar(value='2')
        self.search_from=tk.StringVar(value='1');self.search_to=tk.StringVar(value='231')
        self.search_baud=tk.StringVar(value='19200');self.search_parity=tk.StringVar(value='Even')
        self.search_stop=tk.StringVar(value='1');self.search_timeout=tk.StringVar(value='700')
        self.advanced_open=False;self.log='';self.target=None;self.inputs=[]
        self.extended=tk.BooleanVar(value=False)
        self.build(); self.refresh();self.after(90,self.handle)
        self.protocol("WM_DELETE_WINDOW",self.on_close)

    def label(self,p,text,size=12,color=FG,bold=False):
        return tk.Label(p,text=text,bg=p.cget('bg'),fg=color,font=('Segoe UI',size,'bold' if bold else 'normal'),anchor='w',justify='left')
    def btn(self,p,label,fn,orange=True):
        normal=ORANGE if orange else '#252525'
        hover='#FF8A36' if orange else '#454545'
        pressed='#FFBC5E'
        b=tk.Button(p,text=label,bg=normal,fg='white',activebackground=hover,activeforeground='white',
            disabledforeground='#8D8D8D',font=('Segoe UI',12,'bold'),relief='flat',cursor='hand2',padx=14,pady=9)
        b._flash_after=None
        def restore():
            b._flash_after=None
            if b.winfo_exists():b.config(bg=normal,fg='white',activebackground=hover,activeforeground='white',relief='flat')
        def press(event=None):
            if str(b.cget('state'))=='disabled':return
            if b._flash_after is not None:b.after_cancel(b._flash_after);b._flash_after=None
            b.config(bg=pressed,fg='#111111',activebackground=pressed,activeforeground='#111111',relief='sunken')
        def release(event=None):
            if b._flash_after is not None:b.after_cancel(b._flash_after)
            b._flash_after=b.after(220,restore)
        def invoke():
            press();b.update_idletasks();release();fn()
        def cleanup(event):
            if b._flash_after is not None:
                b.after_cancel(b._flash_after);b._flash_after=None
        b.configure(command=invoke)
        b.bind('<ButtonPress-1>',press);b.bind('<ButtonRelease-1>',release)
        b.bind('<KeyPress-space>',press);b.bind('<KeyRelease-space>',release)
        b.bind('<Destroy>',cleanup)
        return b
    def panel(self,p):
        f=tk.Frame(p,bg=PANEL,padx=18,pady=15)
        f.pack(fill='x',pady=(0,12));return f
    def build(self):
        canvas=tk.Canvas(self,bg=BG,highlightthickness=0)
        scroll=ttk.Scrollbar(self,command=canvas.yview)
        canvas.configure(yscrollcommand=scroll.set)
        scroll.pack(side='right',fill='y');canvas.pack(fill='both',expand=True)
        outer=tk.Frame(canvas,bg=BG,padx=24,pady=20)
        win=canvas.create_window((0,0),window=outer,anchor='nw')
        outer.bind('<Configure>',lambda e:canvas.configure(scrollregion=canvas.bbox('all')))
        canvas.bind('<Configure>',lambda e:canvas.itemconfigure(win,width=e.width))
        self.bind_all('<MouseWheel>',lambda e:canvas.yview_scroll(-1 if e.delta>0 else 1,'units'))
        top=tk.Frame(outer,bg=BG);top.pack(fill='x',pady=(0,17))
        try:
            self.icon=tk.PhotoImage(file=str(resource_path('assets/climagold_full.png'))).subsample(3,3)
            tk.Label(top,image=self.icon,bg=BG,padx=12,pady=8).pack(anchor='w')
        except tk.TclError:
            self.label(top,'Clima Gold',24,bold=True).pack(anchor='w')
        self.label(top,'ZIEHL-ABEGG  •  Konfigurator',11,ORANGE,True).pack(anchor='w',pady=(5,0))

        a=self.panel(outer)
        self.label(a,'1. Wykryj silnik',18,bold=True).pack(anchor='w',pady=(0,10))
        row=tk.Frame(a,bg=PANEL);row.pack(fill='x')
        self.port=ttk.Combobox(row,textvariable=self.com,state='readonly',width=17,font=('Segoe UI',11))
        self.port.pack(side='left',fill='x',expand=True,padx=(0,8))
        self.port.bind('<<ComboboxSelected>>',lambda event:self.port_changed())
        self.search_btn=self.btn(row,'WYKRYJ SILNIK',self.search);self.search_btn.pack(side='left')
        self.label(a,'',4).pack()
        self.label(a,'Wynik:',11,MUTED).pack(anchor='w')
        self.device_label=self.label(a,'',15,bold=True)
        self.device_label.pack(fill='x');self.device_label.configure(textvariable=self.device)
        self.refresh_btn=self.btn(row,'Porty ↻',self.refresh,orange=False)
        self.refresh_btn.pack(side='left',padx=(6,0))
        self.device_details=tk.Text(a,height=7,bg=PANEL,fg=FG,relief='flat',wrap='word',font=('Segoe UI',12))
        self.device_details.pack(fill='x',pady=(8,4));self.device_details.config(state='disabled')
        reports=tk.Frame(a,bg=PANEL);reports.pack(fill='x',pady=3)
        self.btn(reports,'Kopiuj raport',self.copy_log,False).pack(side='left',fill='x',expand=True,padx=(0,3))
        self.btn(reports,'Pokaż / zapisz raport',self.show_log,False).pack(side='left',fill='x',expand=True,padx=(3,0))
        self.stop_btn=self.btn(a,'Przerwij wyszukiwanie',self.cancel_scan,False)
        self.stop_btn.pack(fill='x',pady=3)

        b=self.panel(outer)
        self.label(b,'2. Ustaw adres i zapisz',18,bold=True).pack(anchor='w',pady=(0,10))
        shortcuts=tk.Frame(b,bg=PANEL);shortcuts.pack(fill='x')
        self.nawiew_btn=self.btn(shortcuts,'NAWIEW 231',lambda:self.apply_preset(231))
        self.nawiew_btn.pack(side='left',fill='x',expand=True,padx=(0,4))
        self.wywiew_btn=self.btn(shortcuts,'WYWIEW 232',lambda:self.apply_preset(232))
        self.wywiew_btn.pack(side='left',fill='x',expand=True,padx=(4,0))
        self.label(b,'Skróty: 9600 / None / 2. Adres poniżej zawsze można edytować.',10,MUTED).pack(anchor='w',pady=(8,10))
        self.factory_btn=self.btn(b,'USTAWIENIA FABRYCZNE — 247 / 19200 / Even / 1',self.select_factory,False)
        self.factory_btn.pack(fill='x',pady=(0,8))
        self.manual_frame=tk.Frame(b,bg=PANEL)
        for lab,var,options in [('Adres',self.manual_addr,None),('Baudrate',self.manual_baud,['4800','9600','19200']),
                                 ('Parity',self.manual_parity,['None','Even','Odd']),('Stop bits',self.manual_stop,['1','2'])]:
            r=tk.Frame(self.manual_frame,bg=PANEL);r.pack(fill='x',pady=4)
            self.label(r,lab,11,MUTED).pack(side='left')
            if options: w=ttk.Combobox(r,textvariable=var,values=options,state='readonly',width=12)
            else:w=tk.Entry(r,textvariable=var,width=14)
            w.pack(side='right');self.inputs.append(w)
        self.manual_frame.pack(fill='x',pady=(4,8))
        self.change_btn=self.btn(b,'ZMIEŃ PARAMETRY',self.program)
        self.change_btn.pack(fill='x',pady=(8,0))

        c=self.panel(outer)
        self.details_btn=self.btn(c,'▸  Więcej ustawień',self.toggle_advanced,False)
        self.details_btn.pack(fill='x')
        self.advanced=tk.Frame(c,bg=PANEL)
        self.label(self.advanced,'Ręczne wyszukiwanie: zakres adresów i połączenie',11,MUTED).pack(anchor='w',pady=(12,6))
        for lab,var,options in [('Adres od',self.search_from,None),('Adres do',self.search_to,None),
                      ('Baudrate',self.search_baud,['19200','9600','4800','38400']),
                      ('Parity',self.search_parity,['Even','None','Odd']),
                      ('Stop bits',self.search_stop,['1','2']),('Czas (ms)',self.search_timeout,None)]:
            r=tk.Frame(self.advanced,bg=PANEL);r.pack(fill='x',pady=3)
            self.label(r,lab,11).pack(side='left')
            if options: w=ttk.Combobox(r,textvariable=var,values=options,width=13,state='readonly')
            else:w=tk.Entry(r,textvariable=var,width=15)
            w.pack(side='right')
        self.manual_search_btn=self.btn(self.advanced,'Szukaj w zakresie',self.manual_search,False)
        self.manual_search_btn.pack(fill='x',pady=(9,3))
        tk.Checkbutton(self.advanced,text='Rozszerzone profile automatycznego skanowania',variable=self.extended,bg=PANEL,fg=FG,selectcolor=PANEL).pack(anchor='w',pady=5)
        self.btn(self.advanced,'Pokaż raport techniczny',self.show_log,False).pack(fill='x',pady=3)

        self.status_label=self.label(outer,'',12,MUTED)
        self.status_label.pack(fill='x',pady=6)
        self.status_label.configure(textvariable=self.status,wraplength=580)
        self.update_ui()

    def on_close(self):
        if self.busy and self.detected is not None:
            self.status.set('Trwa zapis i kontrola. Poczekaj na wynik przed zamknięciem.')
            return
        self.stop.set();self.destroy()
    def port_changed(self):
        if self.detected is not None and self.detected.port != self.com.get():
            self.detected=None;self.device.set('Zmieniono port. Wykryj silnik ponownie.')
            self.describe();self.update_ui()
    def apply_preset(self,address):
        if self.busy:return
        self.manual_addr.set(str(address));self.manual_baud.set('9600')
        self.manual_parity.set('None');self.manual_stop.set('2')
    def select_factory(self):
        if self.busy:return
        self.manual_addr.set('247');self.manual_baud.set('19200')
        self.manual_parity.set('Even');self.manual_stop.set('1')
        self.status.set('Ustawiono fabryczną komunikację 247 / 19200 / Even / 1. Aby zapisać, użyj „ZMIEŃ PARAMETRY”.')
        messagebox.showinfo('Ustawienia fabryczne komunikacji',
            'Przygotowano: 247 / 19200 / Even / 1.\n\n'
            'Nie wysłano żadnego zapisu. To ustawienia komunikacji, nie pełny reset silnika.\n'
            'Zapis wykonasz przyciskiem „ZMIEŃ PARAMETRY”. Kodowanie obejmuje także CM=3.')
    def target_word(self):
        return encode_h3(int(self.manual_addr.get()),int(self.manual_baud.get()),
                '8'+{'None':'N','Even':'E','Odd':'O'}[self.manual_parity.get()]+self.manual_stop.get())
    def cancel_scan(self):
        self.stop.set();self.status.set('Zatrzymywanie wyszukiwania...')
    def describe(self):
        d=self.detected
        self.device_details.config(state='normal');self.device_details.delete('1.0','end')
        if d:
            saved=f'{d.saved.address} / {d.saved.baud} / {d.saved.mode}' if d.saved else 'niepotwierdzone'
            raw=f'0x{d.raw_h3:04X}' if d.raw_h3 is not None else 'brak'
            cm=f'{d.control_word & 15} / 3' if d.control_word is not None else 'brak odczytu / 3'
            details=f'Model: {d.model_name}\nFirmware: {d.firmware if d.firmware is not None else "brak odczytu"}\nPort: {d.port}\nAktywne połączenie: {d.active_info}\nZapisane h3: {saved} ({raw})\nCM (odczyt / cel): {cm}'
            self.device_details.insert('1.0',details)
        self.device_details.config(state='disabled')
    def copy_log(self):
        self.clipboard_clear();self.clipboard_append(self.log or 'Brak raportu. Najpierw wykryj silnik.')
        self.status.set('Raport skopiowany do schowka.')
    def save_log(self):
        name=filedialog.asksaveasfilename(defaultextension='.txt',initialfile='ClimaGold_raport.txt',filetypes=[('Raport tekstowy','*.txt')])
        if name:
            try:Path(name).write_text(self.log or 'Brak raportu',encoding='utf-8')
            except OSError as error:messagebox.showwarning('Błąd zapisu raportu',str(error))
    def toggle_advanced(self):
        self.advanced_open=not self.advanced_open
        if self.advanced_open:self.advanced.pack(fill='x');self.details_btn.configure(text='▾  Ukryj ustawienia')
        else:self.advanced.pack_forget();self.details_btn.configure(text='▸  Więcej ustawień')
    def refresh(self):
        if self.busy:return
        try: found=ports()
        except Exception as e:found=[];self.status.set(str(e))
        self.port['values']=found
        if self.com.get() not in found:self.com.set(found[0] if found else '')
        if not found:self.status.set('Nie znaleziono kabla USB–RS485.')
        self.port_changed()
    def update_ui(self):
        self.change_btn.config(state='disabled' if self.busy or not self.detected else 'normal')
        for button in (self.search_btn,self.refresh_btn,self.nawiew_btn,self.wywiew_btn,self.factory_btn,self.manual_search_btn):
            button.config(state='disabled' if self.busy else 'normal')
        self.port.config(state='disabled' if self.busy else 'readonly')
        self.stop_btn.config(state='normal' if self.busy and self.detected is None else 'disabled')
        for widget in self.inputs:
            widget.config(state='disabled' if self.busy else 'readonly' if isinstance(widget,ttk.Combobox) else 'normal')
    def background(self,worker):
        if self.busy:return
        self.busy=True;self.update_ui()
        def run():
            try:worker()
            except Exception as e:self.events.put(('error',str(e)))
            finally:self.events.put(('idle',None))
        threading.Thread(target=run,daemon=True).start()
    def search(self):
        if self.busy:return
        if not self.com.get():messagebox.showwarning('Brak kabla','Wybierz port COM.');return
        self.detected=None;self.update_ui();self.device.set('Wyszukiwanie...')
        self.stop.clear();p=self.com.get();extended=self.extended.get()
        self.background(lambda:self.events.put(('found', auto_scan(p,lambda x:self.events.put(('progress',x)),self.stop,extended))))
    def manual_search(self):
        if self.busy:return
        if not self.com.get():messagebox.showwarning('Brak kabla','Wybierz port COM.');return
        try:
            a,b=validate_range(self.search_from.get(),self.search_to.get())
            baud=int(self.search_baud.get());pr={'None':'N','Even':'E','Odd':'O'}[self.search_parity.get()]
            stop=int(self.search_stop.get());timeout=int(self.search_timeout.get())
            if timeout<150 or timeout>3000: raise ValueError('Czas 150–3000 ms')
        except (ValueError,KeyError) as e:messagebox.showwarning('Błąd',str(e));return
        self.detected=None;self.update_ui();self.device.set('Wyszukiwanie...')
        self.stop.clear();p=self.com.get()
        self.background(lambda:self.events.put(('found',manual_scan(p,a,b,baud,pr,stop,timeout,
                                               lambda x:self.events.put(('progress',x)),self.stop))))
    def program(self):
        d=self.detected
        if not d or self.busy:return
        if not eligible(d):
            messagebox.showwarning('Zapis niedostępny','Wykryto urządzenie, lecz brak potwierdzonej obsługi zapisu. Użyj ZAset.');return
        try:
            target=self.target_word()
            v=decode_h3(target)
        except Exception as e:messagebox.showwarning('Błędne dane',str(e));return
        if not messagebox.askyesno('Potwierdź zmianę',
             f'Nowy adres: {v.address}\nParametry: {v.baud} / {v.mode}\nCM: 3\n\n'
             'Czy podłączony jest tylko jeden zatrzymany silnik, odłączony od działającej centrali?\n'
             'Po zmianie program sprawdzi rezultat.'):
            return
        self.target=target
        self.status.set('Zmieniam parametry i sprawdzam nowe połączenie...')
        self.background(lambda:self.events.put(('programmed',program_one_click(d,target,
                                       lambda s:self.events.put(('progress',s))))))
    def show_log(self):
        popup=tk.Toplevel(self);popup.title('Raport techniczny');popup.geometry('660x340')
        buttons=tk.Frame(popup);buttons.pack(fill='x')
        ttk.Button(buttons,text='Kopiuj',command=self.copy_log).pack(side='left')
        ttk.Button(buttons,text='Zapisz TXT',command=self.save_log).pack(side='left')
        text=tk.Text(popup,wrap='word');text.pack(fill='both',expand=True)
        text.insert('1.0',self.log or 'Brak raportu');text.config(state='disabled')
    def handle(self):
        try:
            while True:
                k,x=self.events.get_nowait()
                if k=='progress':self.status.set(x)
                elif k=='found':
                    x,metrics=x
                    self.detected=x;self.device.set(f'Znaleziono silnik • adres {x.active_address}' if x else 'Nie znaleziono silnika')
                    self.status.set('Silnik gotowy do konfiguracji.' if x and eligible(x) else
                       'Wykryto urządzenie bez obsługi zapisu.' if x else 'Sprawdź kabel i wyszukiwanie ręczne.')
                    if x:
                        self.manual_addr.set(str(x.active_address));self.manual_baud.set(str(x.baud))
                        self.manual_parity.set({'N':'None','E':'Even','O':'Odd'}[x.parity]);self.manual_stop.set(str(x.stopbits))
                    self.describe()
                    self.log=f'Clima Gold Windows CM3 TEST\nSkanowanie: {metrics}\n'+self.device_details.get('1.0','end').strip()
                    if x:messagebox.showinfo('Wykryto silnik',f'Adres: {x.active_address}\nPołączenie: {x.baud} / {x.parity} / {x.stopbits}')
                elif k=='programmed':
                    self.log=x.details
                    messagebox.showinfo('Wynik operacji',x.message)
                    self.status.set(x.message)
                    if x.state in ('ok','already') and self.detected is not None:
                        target=decode_h3(self.target)
                        self.detected=replace(self.detected,active_address=target.address,baud=target.baud,
                            parity=target.mode[1],stopbits=int(target.mode[2]),raw_h3=self.target,saved=target,
                            function=3,register=3,exception=0,control_word=x.control_word)
                        self.device.set(f'Potwierdzono adres {target.address}');self.describe()
                    elif x.attempted:
                        self.detected=None;self.device.set('Zmiana niepotwierdzona. Wykryj silnik ponownie.');self.describe()
                elif k=='error':
                    self.status.set(x);messagebox.showwarning('Nie udało się',x)
                    self.detected=None
                elif k=='idle':self.busy=False
                self.update_ui()
        except queue.Empty:pass
        self.after(90,self.handle)

if __name__=='__main__':SimpleApp().mainloop()
