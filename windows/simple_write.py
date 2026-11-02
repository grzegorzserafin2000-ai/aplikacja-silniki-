"""ECblue programming: one write per stage, fresh readback, bounded polling."""
import time
from dataclasses import dataclass
from protocol import H0_PIN, H3, PIN_LEVEL_1, decode_h3, ModbusError, ModbusException
from serial_service import transact, capture_trace
from model_profile import CONFIRMED_H3_PRODUCT_CODES

@dataclass(frozen=True)
class Result:
    state: str
    message: str
    details: str
    attempted: bool


def eligible(d):
    if d is None or d.product_code not in CONFIRMED_H3_PRODUCT_CODES or d.raw_h3 is None:
        return False
    try:
        return decode_h3(d.raw_h3) == d.saved
    except (ModbusError, ValueError, TypeError):
        return False


def program_one_click(d, target, progress=lambda msg: None, *, clock=None):
    """No automatic FC06 retransmission, including after lost acknowledgements."""
    if not eligible(d):
        raise ModbusError('Niepotwierdzony profil silnika. Zapis niedostępny.')
    dest = decode_h3(target)
    if dest.baud in (38400, 115200):
        raise ModbusError('Niepotwierdzona obsługa docelowej prędkości.')
    import serial
    clock = clock or time
    log = [f'Clima Gold Windows 2.6 TEST; aktywny {d.active_info if hasattr(d,"active_info") else d.active_address}; h3=0x{d.raw_h3:04X}',
           f'Cel {dest.address} / {dest.baud} / {dest.mode} / 0x{target:04X}']
    attempted = False
    live = (d.active_address, d.baud, d.parity, d.stopbits)
    goal = (dest.address, dest.baud, dest.mode[1], int(dest.mode[2]))

    def result(state, message):
        return Result(state, message, '\n'.join(log), attempted)

    try:
        with capture_trace(log.append), serial.Serial(port=d.port, baudrate=d.baud, bytesize=8,
                parity=d.parity, stopbits=d.stopbits, timeout=.05, write_timeout=1.5) as conn:
            current = [d.baud, d.parity, d.stopbits]

            def select(link):
                params = list(link[1:])
                if current != params:
                    conn.baudrate, conn.parity, conn.stopbits = params
                    current[:] = params
                    clock.sleep(.1)
                log.append(f'LINK {link[0]} / {params[0]} / {params[1]} / {params[2]}')

            def request(link, fn, register, value=1, timeout=.5):
                select(link)
                return transact(conn, link[0], fn, register, value, timeout=timeout)

            def read(link, fn=3, register=H3):
                try:
                    return request(link, fn, register)
                except (TimeoutError, ModbusError, ValueError, OSError) as error:
                    log.append(f'Odczyt: {error}')
                    return None

            def confirmed(link):
                return read(link, 4, 1) == d.product_code and read(link) == target

            progress('Sprawdzam silnik i tryb transmisji...')
            if read(live, 4, 1) != d.product_code or read(live) != d.raw_h3:
                return result('blocked', 'Dane silnika zmieniły się. Wykryj go ponownie.')
            saved = decode_h3(d.raw_h3)
            saved_link = (saved.address, saved.baud, saved.mode[1], int(saved.mode[2]))
            if saved.address == live[0] and saved_link != live:
                log.append('Tryb wykryty różni się od h3; sprawdzam zapisany tryb bez zapisu.')
                if read(saved_link, 4, 1) == d.product_code and read(saved_link) == d.raw_h3:
                    live = saved_link
                    log.append('Tryb z h3 potwierdzony i1+h3; wybrany do zapisu.')
                elif read(live, 4, 1) != d.product_code or read(live) != d.raw_h3:
                    return result('blocked', 'Nie potwierdzono stabilnego połączenia. Bez zapisu.')
            if target == d.raw_h3 and live == goal:
                return result('already', 'Silnik już ma podane ustawienia. Nie wykonano zapisu.')

            if target != d.raw_h3:
                progress('Zapisuję ustawienia...')
                attempted = True
                try:
                    request(live, 6, H0_PIN, PIN_LEVEL_1, timeout=.9)
                except (TimeoutError, ModbusError, OSError) as error:
                    log.append(f'PIN 1234: {error}')
                    return result('blocked', 'Brak potwierdzenia PIN 1234. Nie wysłano zapisu h3.')
                clock.sleep(.5)
                try:
                    request(live, 6, H3, target, timeout=1.1)
                    log.append('h3 FC06: ACK; nadal wymagany odczyt.')
                except ModbusException as error:
                    log.append(str(error))
                    return result('rejected', 'Silnik odrzucił zapis h3. Aktywacji nie wysłano.')
                except (TimeoutError, ModbusError, OSError) as error:
                    log.append(f'h3 FC06 bez pewnego potwierdzenia: {error}')
                deadline = clock.monotonic() + 5
                stored = False
                while True:
                    raw = read(live)
                    log.append(f'Odczyt h3: {"brak" if raw is None else f"0x{raw:04X}"}')
                    if raw == target:
                        stored = True
                        break
                    if live != goal and confirmed(goal):
                        return result('ok', f'Potwierdzono {dest.address} / {dest.baud} / {dest.mode}.')
                    remaining = deadline - clock.monotonic()
                    if remaining <= 0:
                        break
                    clock.sleep(min(.2, remaining))
                if not stored:
                    return result('uncertain', 'Nie potwierdzono nowego h3. Aktywacji nie wysłano. Skopiuj raport.')
            else:
                log.append('Cel już zapisany w h3; bez ponownego zapisu h3 i PIN 1234.')

            progress('Aktywuję i sprawdzam nowe połączenie...')
            attempted = True
            try:
                request(live, 6, H0_PIN, 3698, timeout=1.2)
                log.append('PIN 3698: ACK; kontrola nowego połączenia.')
            except (TimeoutError, ModbusError, OSError) as error:
                log.append(f'PIN 3698 bez pewnego ACK: {error}')
            deadline = clock.monotonic() + 15
            while True:
                clock.sleep(min(.2, max(0, deadline-clock.monotonic())))
                if confirmed(goal):
                    return result('ok', f'Potwierdzono {dest.address} / {dest.baud} / {dest.mode}.')
                if clock.monotonic() >= deadline:
                    break
            if read(live, 4, 1) == d.product_code and read(live) == target:
                return result('pending', 'Nowe h3 jest zapisane, ale nadal odpowiada poprzednie połączenie. Aktywacja niepotwierdzona.')
            return result('uncertain', 'Nie potwierdzono nowego połączenia. Sprawdź stary i nowy adres; nie ponawiaj zapisu w ciemno.')
    except (OSError, ModbusError, ValueError) as error:
        log.append(f'Błąd: {error}')
        return result('uncertain' if attempted else 'blocked', f'Operacja przerwana: {error}. Wykryj silnik ponownie.')
