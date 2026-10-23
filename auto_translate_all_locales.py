import xml.etree.ElementTree as ET
import glob
import os
import re
import urllib.request
import urllib.parse
import json
import time

default_file = 'app/src/main/res/values/strings.xml'
tree = ET.parse(default_file)
root = tree.getroot()

default_strings = {}
for e in root.findall('string'):
    name = e.attrib.get('name')
    if name:
        default_strings[name] = e.text or ''

# Non-translatable keys or keys that shouldn't be altered
NON_TRANSLATABLE = {
    'app_name', 'app_version', 'camera_aspect_4_3', 'camera_aspect_16_9',
    'camera_rec_label', 'camera_rec_time_default', 'camera_timer_3s',
    'camera_timer_10s', 'editor_coord_decimal', 'editor_coord_dms',
    'paywall_bullet_video'
}

# Android locale code to Google Translate lang code mapping
LANG_MAP = {
    'in': 'id',
    'iw': 'he',
    'zh': 'zh-CN',
    'nb': 'no',
}

def escape_xml(text):
    if not text:
        return ""
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    parts = re.split(r"(\\')", text)
    res = []
    for p in parts:
        if p == "\\'":
            res.append(p)
        else:
            res.append(p.replace("'", "\\'"))
    return "".join(res)

def translate_text(text, target_lang):
    if not text or text in NON_TRANSLATABLE or target_lang == 'en':
        return text

    # Preserve format placeholders like %1$s, %2$d, \n, etc.
    # Replace format specifiers with temporary tokens
    placeholders = re.findall(r'%[0-9]+\$[sd]', text)
    temp_text = text
    for i, ph in enumerate(placeholders):
        temp_text = temp_text.replace(ph, f'__PH_{i}__')

    # Convert \n to SPACE_NL_SPACE so translator doesn't strip it
    temp_text = temp_text.replace('\n', ' ___NL___ ')

    # Request Google Translate
    gt_lang = LANG_MAP.get(target_lang, target_lang)
    url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={gt_lang}&dt=t&q={urllib.parse.quote(temp_text)}"

    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            translated = "".join([part[0] for part in data[0] if part[0]])

            # Restore \n
            translated = re.sub(r'\s*___NL___\s*', '\n', translated)

            # Restore placeholders
            for i, ph in enumerate(placeholders):
                translated = re.sub(rf'__PH_{i}__', ph, translated, flags=re.IGNORECASE)

            return translated.strip()
    except Exception as err:
        return text

base_path = 'app/src/main/res'
dirs = sorted(glob.glob(os.path.join(base_path, 'values-*')))

for val_dir in dirs:
    if val_dir.endswith('values-night'):
        continue
    folder = os.path.basename(val_dir)
    lang_code = folder.replace('values-', '').split('-')[0]
    s_file = os.path.join(val_dir, 'strings.xml')

    if not os.path.exists(s_file):
        continue

    try:
        t = ET.parse(s_file)
        r = t.getroot()
        existing = {e.attrib.get('name'): e.text or '' for e in r.findall('string') if e.attrib.get('name')}

        # Identify strings that are identical to English default
        untranslated = []
        for k, v in default_strings.items():
            if k in NON_TRANSLATABLE:
                continue
            curr_val = existing.get(k, '')
            if curr_val == v and len(v) > 2 and not v.isdigit():
                untranslated.append((k, v))

        if not untranslated:
            print(f"{folder}: 100% translated!")
            continue

        print(f"Translating {len(untranslated)} untranslated strings for {folder} ({lang_code})...")
        translated_map = {}

        for k, orig_v in untranslated:
            trans_v = translate_text(orig_v, lang_code)
            if trans_v and trans_v != orig_v:
                translated_map[k] = trans_v
            time.sleep(0.05) # Rate limit precaution

        if translated_map:
            for elem in r.findall('string'):
                k = elem.attrib.get('name')
                if k in translated_map:
                    elem.text = translated_map[k]

            xml_lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
            for child in r.findall('string'):
                k = child.attrib.get('name')
                v = child.text or ''
                escaped_v = escape_xml(v)
                xml_lines.append(f'    <string name="{k}">{escaped_v}</string>')
            xml_lines.append('</resources>')
            xml_lines.append('')

            with open(s_file, 'w', encoding='utf-8') as f:
                f.write('\n'.join(xml_lines))
            print(f"  Successfully updated {folder} with {len(translated_map)} translations!")

    except Exception as err:
        print(f"Error processing {folder}: {err}")
