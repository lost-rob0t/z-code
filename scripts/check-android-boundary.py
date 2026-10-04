#!/usr/bin/env python3
"""Static boundary assertions; these do not substitute for Android instrumentation."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = ET.parse(root / 'android/app/src/main/AndroidManifest.xml').getroot()
a = '{http://schemas.android.com/apk/res/android}'
app = manifest.find('application')
assert app is not None
assert app.attrib[a + 'allowBackup'] == 'false'
assert app.attrib[a + 'usesCleartextTraffic'] == 'false'
assert {p.attrib[a + 'name'] for p in manifest.findall('uses-permission')} == {'android.permission.INTERNET'}
assert app.find('activity').attrib[a + 'launchMode'] == 'singleTask'
source = (root / 'android/app/src/main/java/actor/starintel/zcode/remote/MainActivity.java').read_text()
for forbidden in ('addJavascriptInterface(', 'handler.proceed(', 'setAllowFileAccess(true)',
                  'setAllowContentAccess(true)', 'evaluateJavascript(', 'web.saveState(', 'Log.'):
    assert forbidden not in source, forbidden
for required in ('handler.cancel()', 'MIXED_CONTENT_NEVER_ALLOW', 'FLAG_SECURE',
                 'setWebContentsDebuggingEnabled(false)', 'LinkPolicy.sameOrigin(', 'request.deny()'):
    assert required in source, required
for xml in (root / 'android/app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
print('PASS: Android manifest, XML and native-bridge boundary assertions')
