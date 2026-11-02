"""Windows GUI entry point; no console, startup errors saved to LOCALAPPDATA."""
import json
import os
import sys
import traceback
from pathlib import Path
from datetime import datetime


def main():
    smoke = '--smoke-test' in sys.argv
    try:
        if os.name == 'nt':
            import ctypes
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID('ClimaGold.CM3.1')
        from simple_app import SimpleApp
        app = SimpleApp()
        if smoke:
            app.withdraw()
            app.update()
            assert app.title() == 'Clima Gold Ziehl-Abegg'
            assert app.icon.width() == 400
            app.apply_preset(231)
            app.manual_addr.set('230')
            from protocol import decode_h3
            assert decode_h3(app.target_word()).address == 230
            assert app.manual_frame.winfo_manager() == 'pack'
            from tkinter import messagebox
            original_info = messagebox.showinfo
            try:
                messagebox.showinfo = lambda *args, **kwargs: None
                app.factory_btn.invoke()
            finally:
                messagebox.showinfo = original_info
            factory = decode_h3(app.target_word())
            assert (factory.address, factory.baud, factory.mode) == (247, 19200, '8E1')
            app.destroy()
            if '--smoke-report' in sys.argv:
                target = Path(sys.argv[sys.argv.index('--smoke-report') + 1])
                target.write_text(json.dumps({'version': '1.0.1 CM3 TEST', 'gui_started': True, 'logo_loaded': True,
                    'editable_address_after_preset': True, 'factory_communication_preset': True}), encoding='utf-8')
            return 0
        app.mainloop()
        return 0
    except Exception:
        report = traceback.format_exc()
        log_dir = Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'ClimaGold_ZIEHL_CM3' / 'logs'
        try:
            log_dir.mkdir(parents=True, exist_ok=True)
            (log_dir / 'ostatni_blad.txt').write_text(datetime.now().isoformat(timespec='seconds') + '\n\n' + report, encoding='utf-8')
        except OSError:
            pass
        if smoke:
            print(report, file=sys.stderr)
        else:
            try:
                from tkinter import Tk, messagebox
                root = Tk(); root.withdraw()
                messagebox.showerror('Clima Gold - blad uruchomienia', report[-1600:] + '\n\nRaport: ' + str(log_dir / 'ostatni_blad.txt'))
                root.destroy()
            except Exception:
                pass
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
