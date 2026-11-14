"""차량 앱 아이콘(tokcar/src/main/res/drawable/tk_icon.xml, 안드로이드 벡터)을 Flutter 앱이 쓰는 SVG(assets/tk_icon.svg)로 바꾼다.
아이콘 모양을 바꿀 때는 tokcar/tools/make_icon.py 를 고쳐 다시 만든 뒤 이 스크립트를 다시 실행.
실행: python3 tools/vector_to_svg.py   (tokcar_mobile/ 안에서)
"""
import os
import xml.etree.ElementTree as ET

# 이 프로젝트(tokcar_mobile/)는 차량 앱 저장소(appa-eonjewa/)와 나란한 폴더에 있다(저장소 밖).
PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CAR = os.path.join(os.path.dirname(PROJ), 'appa-eonjewa')
SRC = os.path.join(CAR, 'tokcar/src/main/res/drawable/tk_icon.xml')
DST = os.path.join(PROJ, 'assets/tk_icon.svg')
A = '{http://schemas.android.com/apk/res/android}'


def color(c):
    """#AARRGGBB / #RRGGBB -> (#RRGGBB, alpha)"""
    c = c.lstrip('#')
    if len(c) == 8:
        return '#' + c[2:], int(c[:2], 16) / 255
    return '#' + c, 1.0


defs, body, gid = [], [], [0]


def grad(el):
    g = el.find('gradient')
    gid[0] += 1
    i = 'g%d' % gid[0]
    stops = ''.join('<stop offset="%s" stop-color="%s" stop-opacity="%.3f"/>' % ((it.get(A + 'offset'),) + color(it.get(A + 'color')))
                    for it in g.findall('item'))
    defs.append('<linearGradient id="%s" gradientUnits="userSpaceOnUse" x1="%s" y1="%s" x2="%s" y2="%s">%s</linearGradient>'
                % (i, g.get(A + 'startX'), g.get(A + 'startY'), g.get(A + 'endX'), g.get(A + 'endY'), stops))
    return 'url(#%s)' % i


def path(p):
    at = {'d': p.get(A + 'pathData')}
    fill = p.get(A + 'fillColor')
    stroke = p.get(A + 'strokeColor')
    for attr in p.findall('{http://schemas.android.com/aapt}attr'):
        name = attr.get('name')
        if name == 'android:fillColor':
            fill = grad(attr)
        elif name == 'android:strokeColor':
            stroke = grad(attr)
    if fill and fill.startswith('#'):
        at['fill'], a = color(fill)
        if a < 1: at['fill-opacity'] = '%.3f' % a
    elif fill:
        at['fill'] = fill
    else:
        at['fill'] = 'none'
    if p.get(A + 'fillAlpha'): at['fill-opacity'] = p.get(A + 'fillAlpha')
    if stroke and stroke.startswith('#'):
        at['stroke'], a = color(stroke)
        if a < 1: at['stroke-opacity'] = '%.3f' % a
    elif stroke:
        at['stroke'] = stroke
    for k, s in (('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap'), ('strokeLineJoin', 'stroke-linejoin')):
        if p.get(A + k): at[s] = p.get(A + k)
    return '<path %s/>' % ' '.join('%s="%s"' % kv for kv in at.items())


def walk(el, out):
    for ch in el:
        if ch.tag == 'path':
            out.append(path(ch))
        elif ch.tag == 'group':
            t = 'translate(%s %s) scale(%s %s)' % (ch.get(A + 'translateX', '0'), ch.get(A + 'translateY', '0'), ch.get(A + 'scaleX', '1'), ch.get(A + 'scaleY', '1'))
            inner = []
            walk(ch, inner)
            out.append('<g transform="%s">%s</g>' % (t, ''.join(inner)))


root = ET.parse(SRC).getroot()
walk(root, body)
w, h = root.get(A + 'viewportWidth'), root.get(A + 'viewportHeight')
svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %s %s" width="512" height="512"><defs>%s</defs>%s</svg>\n'
       % (w, h, ''.join(defs), ''.join(body)))
os.makedirs(os.path.dirname(DST), exist_ok=True)
open(DST, 'w').write(svg)
print('written', DST, len(svg))
