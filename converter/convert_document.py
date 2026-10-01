"""Load with explicit UNO policies: never execute macros or update document links."""
from pathlib import Path
import subprocess
import sys
import time
import uuid
import uno
from com.sun.star.beans import PropertyValue
from com.sun.star.document.MacroExecMode import NEVER_EXECUTE
from com.sun.star.document.UpdateDocMode import NO_UPDATE


def prop(name, value):
    result = PropertyValue()
    result.Name, result.Value = name, value
    return result


def convert(source, destination, export_filter, profile):
    pipe = "convert_" + uuid.uuid4().hex
    office = subprocess.Popen(["/usr/bin/soffice", "-env:UserInstallation=" + profile.as_uri(),
        "--headless", "--nologo", "--nodefault", "--nofirststartwizard",
        "--accept=pipe,name=" + pipe + ";urp;StarOffice.ComponentContext"], stdin=subprocess.DEVNULL)
    desktop = None
    document = None
    try:
        local = uno.getComponentContext()
        resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
        for attempt in range(100):
            try:
                context = resolver.resolve("uno:pipe,name=" + pipe + ";urp;StarOffice.ComponentContext")
                break
            except Exception:
                if office.poll() is not None or attempt == 99:
                    raise RuntimeError("LibreOffice did not start")
                time.sleep(0.1)
        desktop = context.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", context)
        document = desktop.loadComponentFromURL(source.as_uri(), "_blank", 0, (
            prop("Hidden", True), prop("ReadOnly", True),
            prop("MacroExecutionMode", NEVER_EXECUTE), prop("UpdateDocMode", NO_UPDATE)))
        if document is None:
            raise ValueError("Unsupported or protected document")
        document.storeToURL(destination.as_uri(), (prop("FilterName", export_filter), prop("Overwrite", True)))
    finally:
        if document is not None:
            try:
                document.close(True)
            except Exception:
                document.dispose()
        if desktop is not None:
            desktop.terminate()
        try:
            office.wait(timeout=10)
        except subprocess.TimeoutExpired:
            office.kill()
            office.wait()


if __name__ == "__main__":
    source, destination, export_filter, profile = sys.argv[1:]
    convert(Path(source).resolve(), Path(destination).resolve(), export_filter, Path(profile).resolve())
