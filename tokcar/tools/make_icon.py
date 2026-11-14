"""TokCar 아이콘(106번, 하늘색 테마)을 안드로이드 벡터로 만든다.
prototype/tokcar_app.html 의 APP_ICON(120x120)과 같은 모양·색. 결과:
  res/drawable/tk_icon.xml              앱 안에서 쓰는 아이콘(배경 포함, 120x120)
  res/drawable/ic_launcher_foreground.xml  런처 적응형 아이콘 앞면(108dp, 가운데 84dp에 맞춤)
  res/drawable/ic_launcher_background.xml  런처 적응형 아이콘 바탕(하늘색 그라데이션)
실행: python3 tokcar/tools/make_icon.py
"""
import os

BG = ('#3A5FB0', '#14254F')       # 바탕: 파랑 -> 남색(시안의 「남색」 테마)
FACE = ('#FFFFFF', '#C3CAD6')     # 차·말풍선 면: 흰색 -> 연회색
LINE = ('#5A7CC8', '#16295A')     # 외곽선: 밝은 남색 -> 진한 남색
FEAT = '#2A4C9A'                  # 눈·입·앞유리
ROAD = '#000000'                  # 도로(투명도 .22)

def hexa(c, a=1.0):
    return '#%02X%s' % (round(a * 255), c[1:].upper())

def grad(x1, y1, x2, y2, c):
    return ('<aapt:attr name="android:%s"><gradient android:type="linear" android:startX="%s" android:startY="%s" '
            'android:endX="%s" android:endY="%s"><item android:offset="0" android:color="%s"/><item android:offset="1" android:color="%s"/></gradient></aapt:attr>'
            % ('{attr}', x1, y1, x2, y2, hexa(c[0]), hexa(c[1])))

def path(d, fill=None, stroke=None, sw=None, cap=None, join=None, fill_g=None, stroke_g=None, alpha=None):
    a = ['<path android:pathData="%s"' % d]
    if fill: a.append(' android:fillColor="%s"' % fill)
    if alpha is not None: a.append(' android:fillAlpha="%s"' % alpha)
    if stroke: a.append(' android:strokeColor="%s"' % stroke)
    if sw: a.append(' android:strokeWidth="%s"' % sw)
    if cap: a.append(' android:strokeLineCap="%s"' % cap)
    if join: a.append(' android:strokeLineJoin="%s"' % join)
    if not fill_g and not stroke_g:
        return ''.join(a) + '/>'
    inner = (fill_g.replace('{attr}', 'fillColor') if fill_g else '') + (stroke_g.replace('{attr}', 'strokeColor') if stroke_g else '')
    return ''.join(a) + '>' + inner + '</path>'

def circle(cx, cy, r):
    return 'M%s,%s m-%s,0 a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 -%s,0z' % (cx, cy, r, r, r, 2 * r, r, r, 2 * r)

def rrect(x, y, w, h, r):
    return ('M%s,%s h%s a%s,%s 0 0 1 %s,%s v%s a%s,%s 0 0 1 -%s,%s h-%s a%s,%s 0 0 1 -%s,-%s v-%s a%s,%s 0 0 1 %s,-%sz'
            % (x + r, y, w - 2 * r, r, r, r, r, h - 2 * r, r, r, r, r, w - 2 * r, r, r, r, r, h - 2 * r, r, r, r, r))

def rect(x, y, w, h):
    return 'M%s,%s h%s v%s h-%sz' % (x, y, w, h, w)

BODY = ['M-26 -8L-19 -24Q-17.5 -26-15 -26H15Q17.5 -26 19 -24L26 -8Q30 -7 30 -2V12Q30 16 26 16H-26Q-30 16-30 12V-2Q-30 -7-26 -8Z',
        'M-29.5 -14h-5a2.6 2.6 0 0 0-2.6 2.6v3.4a2.6 2.6 0 0 0 2.6 2.6h5z',
        'M29.5 -14h5a2.6 2.6 0 0 1 2.6 2.6v3.4a2.6 2.6 0 0 1-2.6 2.6h-5z',
        rect(-31, -7.6, 6, 2.2), rect(25, -7.6, 6, 2.2)]
BUBBLE = 'M87 10a20 20 0 1 1-12.9 35.3L68 47l1.7-7A20 20 0 0 1 87 10z'

def art(with_bg):
    out = []
    if with_bg:
        out.append(path('M0,0h120v120h-120z', fill_g=grad(0, 0, 120, 120, BG)))
    out.append(path('M-52 120 49.4 62H51.6L153 120z', fill=ROAD, alpha='0.22'))
    for y1, y2, w in [(111, 120, 3.2), (98, 105, 2.8), (87, 93, 2.4), (77.5, 82, 2.1), (69.5, 73, 1.8)]:
        out.append(path('M50.5 %s V%s' % (y2, y1), stroke=hexa('#FFFFFF', .85), sw=w, cap='round'))
    # 말풍선: 외곽선을 먼저 그리고 면을 위에(paint-order: stroke)
    out.append(path(BUBBLE, stroke_g=grad(67, 10, 107, 50, LINE), sw=3, join='round'))
    out.append(path(BUBBLE, fill_g=grad(67, 10, 107, 50, FACE)))
    out.append(path(circle(77.5, 31, 2.9) + circle(89.5, 31, 2.9), fill=FEAT))
    out.append(path('M79.5 36.5q4 3.2 8 0', stroke=FEAT, sw=2.2, cap='round'))
    # 차: translate(47 76) scale(1.05)
    car = []
    for x in (-24, 13):   # 바퀴(외곽선 + 면)
        car.append(path(rrect(x, 14, 11, 12, 3.5), stroke_g=grad(-30, -26, 30, 26, LINE), sw=2.86, join='round'))
        car.append(path(rrect(x, 14, 11, 12, 3.5), fill_g=grad(-30, -26, 30, 26, FACE)))
    for d in BODY:
        car.append(path(d, fill=FEAT, stroke_g=grad(-30, -26, 30, 26, LINE), sw=2.86, join='round'))
    for d in BODY:
        car.append(path(d, fill_g=grad(-30, -26, 30, 26, FACE)))
    car.append(path('M-20 -9L-14.5 -20.5Q-13.5 -22-11.5 -22H11.5Q13.5 -22 14.5 -20.5L20 -9Z', fill=FEAT))
    car.append(path(circle(-7.5, -0.5, 4) + circle(21, -0.5, 4), fill=FEAT))
    car.append(path('M1.5 7q5.2 4.2 10.4 0', stroke=FEAT, sw=2.4, cap='round'))
    out.append('<group android:translateX="47" android:translateY="76" android:scaleX="1.05" android:scaleY="1.05">' + ''.join(car) + '</group>')
    return out

HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- tokcar/tools/make_icon.py 로 만든 파일. 직접 고치지 말고 스크립트를 고쳐서 다시 만들 것 -->\n'
NS = 'xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"'

def write(name, text):
    p = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'res', 'drawable', name)
    with open(p, 'w') as f:
        f.write(text)

write('tk_icon.xml', HEAD + '<vector %s android:width="120dp" android:height="120dp" android:viewportWidth="120" android:viewportHeight="120">\n%s\n</vector>\n'
      % (NS, '\n'.join(art(True))))
# 런처 앞면: 108dp 칸의 가운데 84dp에 아이콘(120)을 0.7배로 줄여 넣음(적응형 아이콘 가림 영역 밖으로 잘리지 않게)
write('ic_launcher_foreground.xml', HEAD + '<vector %s android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
      '<group android:translateX="12" android:translateY="12" android:scaleX="0.7" android:scaleY="0.7">\n%s\n</group>\n</vector>\n' % (NS, '\n'.join(art(False))))
write('ic_launcher_background.xml', HEAD + '<vector %s android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n%s\n</vector>\n'
      % (NS, path('M0,0h108v108h-108z', fill_g=grad(0, 0, 108, 108, BG))))
print('ok')
