import urllib.request
import json
import zipfile

url = 'https://launchermeta.mojang.com/mc/game/version_manifest.json'
req = urllib.request.Request(url)
with urllib.request.urlopen(req) as response:
    manifest = json.loads(response.read())

version_url = next(v['url'] for v in manifest['versions'] if v['id'] == '1.21.4')
with urllib.request.urlopen(version_url) as response:
    v_data = json.loads(response.read())

client_url = v_data['downloads']['client']['url']
print('Downloading client jar...')
urllib.request.urlretrieve(client_url, 'client.jar')

print('Extracting...')
items = ['orange_banner', 'nether_brick_fence', 'end_rod', 'clock', 'beacon', 'stonecutter']
with zipfile.ZipFile('client.jar', 'r') as z:
    for item in items:
        try:
            content = z.read(f'assets/minecraft/items/{item}.json')
            print(f'--- {item}.json ---')
            print(content.decode(\"utf-8\"))
        except KeyError:
            pass
