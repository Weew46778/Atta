from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import shutil

APK = Path(__file__).resolve().parents[1] / 'releases' / 'Setareh-Koochooloo-v1.0.0.apk'
PAGE = '''<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>دانلود ستاره‌کوچولو</title><style>*{box-sizing:border-box}body{margin:0;background:#f5f2fc;color:#35284f;font-family:Tahoma,sans-serif;display:grid;place-items:center;min-height:100svh;padding:24px;line-height:2}main{background:white;max-width:480px;width:100%;padding:36px 26px;border:1px solid #e8def8;border-radius:24px;text-align:center;box-shadow:0 15px 60px #47326210}h1{font-size:25px;margin:12px 0}p{font-size:14px;color:#776a88}.star{font-size:58px}a{display:block;background:#7860d5;color:white;text-decoration:none;border-radius:12px;padding:16px;margin:24px 0;font-size:17px;font-weight:bold}small{font-size:12px;color:#887b98}.note{background:#f6f3fb;padding:14px;border-radius:12px;font-size:12px;text-align:right}</style><main><div class="star">🌟</div><h1>دانلود ستاره‌کوچولو</h1><p>نسخهٔ ۱.۰.۰ برای اندروید<br>حجم: حدود ۱٫۵ مگابایت</p><a href="./Setareh-Koochooloo-v1.0.0.apk" download="Setareh-Koochooloo-v1.0.0.apk">دریافت فایل نصب APK ↓</a><div class="note">پس از دانلود، فایل را از پوشهٔ Downloads گوشی باز کنید. برنامه برای اندروید ۸ به بالا و Android System WebView به‌روز ساخته شده است. نصب روی گوشی واقعی هنوز تست نشده؛ اولین اجرا را یک بزرگ‌تر بررسی کند.</div><p><small>اگر این صفحه را داخل پیش‌نمایش می‌بینید و دانلود شروع نمی‌شود، ابتدا پیش‌نمایش را در تب جدید باز کنید و دوباره دکمه را بزنید.</small></p></main></html>'''.encode('utf-8')

class Handler(BaseHTTPRequestHandler):
    def respond(self, head=False):
        path = self.path.split('?', 1)[0]
        if path == '/Setareh-Koochooloo-v1.0.0.apk':
            if not APK.is_file():
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Type', 'application/vnd.android.package-archive')
            self.send_header('Content-Disposition', 'attachment; filename="Setareh-Koochooloo-v1.0.0.apk"')
            self.send_header('Content-Length', str(APK.stat().st_size))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            if not head:
                with APK.open('rb') as f:
                    shutil.copyfileobj(f, self.wfile)
        elif path == '/':
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(PAGE)))
            self.end_headers()
            if not head:
                self.wfile.write(PAGE)
        else:
            self.send_error(404)
    def do_GET(self):
        self.respond()
    def do_HEAD(self):
        self.respond(True)

if __name__ == '__main__':
    HTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
