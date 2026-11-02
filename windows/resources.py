"""Resource lookup for source installations and PyInstaller one-file builds."""
from pathlib import Path
import sys

def resource_path(name):
    return Path(getattr(sys, '_MEIPASS', Path(__file__).resolve().parent)) / name
