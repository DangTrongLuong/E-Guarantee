"""Internal byte-only Office converter. No user-controlled paths, commands or URLs."""
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit, parse_qs

MAX_BYTES = 100 * 1024 * 1024
SLOT = threading.BoundedSemaphore(1)
FILTERS = {
    "doc": ("docx", "Office Open XML Text"),
    "xls": ("xlsx", "Calc MS Excel 2007 XML"),
}


def convert(data, source_format):
    target, export_filter = FILTERS[source_format]
    with tempfile.TemporaryDirectory(prefix="convert-") as directory:
        work = Path(directory)
        source = work / ("input." + source_format)
        source.write_bytes(data)
        profile = work / "profile"
        (profile / "user").mkdir(parents=True)
        (profile / "user/registrymodifications.xcu").write_text('''<?xml version="1.0"?>
<oor:items xmlns:oor="http://openoffice.org/2001/registry">
 <item oor:path="/org.openoffice.Office.Common/Security/Scripting">
  <prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop>
  <prop oor:name="DisableMacrosExecution" oor:op="fuse"><value>true</value></prop>
 </item>
 <item oor:path="/org.openoffice.Office.Common/Load">
  <prop oor:name="UpdateDocMode" oor:op="fuse"><value>0</value></prop>
 </item>
 <item oor:path="/org.openoffice.Office.Calc/Content/Update">
  <prop oor:name="Link" oor:op="fuse"><value>1</value></prop>
 </item>
</oor:items>''', encoding="utf-8")
        env = {"PATH": "/usr/bin:/bin", "HOME": str(profile), "LANG": "C.UTF-8",
               "XDG_CACHE_HOME": str(profile / "cache"), "SAL_USE_VCLPLUGIN": "svp"}
        command = ["/usr/bin/python3", str(Path(__file__).with_name("convert_document.py")),
                   str(source), str(work / ("input." + target)), export_filter, str(profile)]
        with tempfile.TemporaryFile() as log:
            process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=log, stderr=log,
                                       cwd=work, env=env, start_new_session=True)
            try:
                code = process.wait(timeout=120)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                raise TimeoutError("Conversion timed out")
        output = work / ("input." + target)
        if code != 0 or not output.is_file() or not 0 < output.stat().st_size <= MAX_BYTES:
            raise ValueError("Conversion failed")
        return output.read_bytes()


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def do_GET(self):
        if self.path == "/health":
            self.respond(200, b"ok")
        else:
            self.respond(404, b"not found")

    def do_POST(self):
        parsed = urlsplit(self.path)
        query = parse_qs(parsed.query)
        formats = query.get("format", [])
        if parsed.path != "/convert" or len(formats) != 1 or formats[0] not in FILTERS or set(query) != {"format"}:
            self.respond(400, b"unsupported conversion")
            return
        if self.headers.get("Transfer-Encoding"):
            self.respond(400, b"content length required")
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            size = 0
        if not 0 < size <= MAX_BYTES:
            self.respond(413, b"invalid size")
            return
        if not SLOT.acquire(blocking=False):
            self.respond(503, b"converter busy")
            return
        try:
            data = self.rfile.read(size)
            if len(data) != size or not data.startswith(bytes.fromhex("d0cf11e0a1b11ae1")):
                self.respond(400, b"invalid legacy Office file")
                return
            self.respond(200, convert(data, formats[0]), "application/octet-stream")
        except TimeoutError:
            self.respond(504, b"conversion timed out")
        except Exception:
            self.respond(422, b"conversion failed")
        finally:
            SLOT.release()

    def respond(self, status, body, content_type="text/plain"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
