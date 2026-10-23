import xml.etree.ElementTree as ET
import glob
import os
import re
import urllib.request
import urllib.parse
import json
from concurrent.futures import ThreadPoolExecutor

default_file = 'app/src/main/res/values/strings.xml'
tree = ET.parse(default_file)
root = tree.getroot()

default_strings = {e.attrib.get('name'): e.text or '' for e in root.findall('string') if e.attrib.get('name')}

NON_TRANSLATABLE = {
    'app_name', 'app_version', 'camera_aspect_4_3', 'camera_aspect_16_9',
    'camera_rec_label', 'camera_rec_time_default', 'camera_timer_3s',
    'camera_timer_10s', 'editor_coord_decimal', 'editor_coord_dms',
    'paywall_bullet_video'
}

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

def translate_batch(texts, target_lang):
    if target_lang == 'en':
        return texts

    gt_lang = LANG_MAP.get(target_lang, target_lang)
    translated_results = []

    for text in texts:
        if not text or text in NON_TRANSLATABLE:
            translated_results.append(text)
            continue

        placeholders = re.findall(r'%[0-9]+\$[sd]', text)
        temp_text = text
        for i, ph in enumerate(placeholders):
            temp_text = temp_text.replace(ph, f'__PH_{i}__')
        temp_text = temp_text.replace('\n', ' ___NL___ ')

        url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={gt_lang}&dt=t&q={urllib.parse.quote(temp_text)}"
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=4) as resp:
                data = json.loads(resp.read().decode('utf-8'))
                translated = "".join([part[0] for part in data[0] if part[0]])
                translated = re.sub(r'\s*___NL___\s*', '\n', translated)
                for i, ph in enumerate(placeholders):
                    translated = re.sub(rf'__PH_{i}__', ph, translated, flags=re.IGNORECASE)
                translated_results.append(translated.strip())
        except Exception:
            translated_results.append(text)

    return translated_results

def process_folder(val_dir):
    folder = os.path.basename(val_dir)
    lang_code = folder.replace('values-', '').split('-')[0]
    s_file = os.path.join(val_dir, 'strings.xml')

    if not os.path.exists(s_file):
        return

    try:
        t = ET.parse(s_file)
        r = t.getroot()
        existing = {e.attrib.get('name'): e.text or '' for e in r.findall('string') if e.attrib.get('name')}

        untranslated_keys = []
        untranslated_vals = []
        for k, v in default_strings.items():
            if k in NON_TRANSLATABLE:
                continue
            curr_val = existing.get(k, '')
            if curr_val == v and len(v) > 2 and not v.isdigit():
                untranslated_keys.append(k)
                untranslated_vals.append(v)

        if not untranslated_keys:
            return f"{folder}: 100% translated!"

        translated_vals = translate_batch(untranslated_vals, lang_code)
        translated_map = dict(zip(untranslated_keys, translated_vals))

        if translated_map:
            for elem in r.findall('string'):
                k = elem.attrib.get('name')
                if k in translated_map and translated_map[k] != default_strings.get(k):
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
            return f"Updated {folder}: translated {len(translated_map)} strings!"

    except Exception as err:
        return f"Error processing {folder}: {err}"

base_path = 'app/src/main/res'
dirs = sorted(glob.glob(os.path.join(base_path, 'values-*')))
dirs = [d for d in dirs if not d.endswith('values-night')]

print(f"Starting batch translation across {len(dirs)} language folders...")

with ThreadPoolExecutor(max_workers=16) as executor:
    results = executor.map(process_folder, dirs)
    for res in results:
        if res:
            print(res)

print("Batch translation complete!")
