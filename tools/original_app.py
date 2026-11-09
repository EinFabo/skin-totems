import io
from flask import Flask, request, send_file, render_template, send_from_directory
from PIL import Image
from pathlib import Path
import zipfile
import json
import uuid
import requests

app = Flask(__name__)

@app.route('/')
def index():
    # Returns the HTML page
    return render_template('index.html')

@app.route('/ads.txt')
def ads_txt():
    return send_from_directory(app.static_folder, 'ads.txt')

def generate_totem_image(uploaded_image, overlay):
    """
    Generates a totem image (32x32) from a Minecraft skin image (uploaded_image) and overlay flag.
    Returns a PIL.Image object.
    """
    # Mapping parameters for head, arms, and body
    mapping = {
        "head": {
            "skin": [8, 8, 8, 8],
            "totem": [8, 1, 16, 16],
            "resize": "nearest",
            "crop": [
                [0, 0, 15, 0], [0, 1], [1, 1], [14, 1], [15, 1], [0, 2], [15, 2], [0, 15, 15, 15]
            ]
        },
        "left_arm": {
            "skin": [44, 20, 3, 9],
            "totem": [8, 17, 3, 9],
            "resize": None,
            "crop": []
        },
        "right_arm": {
            "skin": [36, 52, 3, 9],
            "totem": [21, 17, 3, 9],
            "resize": None,
            "crop": []
        },
        "body": {
            "skin": [20, 20, 8, 12],
            "totem": [12, 16, 8, 12],
            "resize": None,
            "crop": []
        }
    }

    # Empty totem image (32x32 pixels, transparent)
    totem_img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))

    # --- Generate head ---
    head = mapping["head"]
    sx, sy, sw, sh = head["skin"]
    head_img = uploaded_image.crop((sx, sy, sx+sw, sy+sh))
    if head["resize"] == "nearest":
        target_w, target_h = head["totem"][2], head["totem"][3]
        head_img = head_img.resize((target_w, target_h), Image.NEAREST)
    head_img = head_img.convert("RGBA")
    pixels = head_img.load()
    for crop in head["crop"]:
        if len(crop) == 4:
            x1, y1, x2, y2 = crop
            for x in range(x1, x2+1):
                for y in range(y1, y2+1):
                    if 0 <= x < head_img.width and 0 <= y < head_img.height:
                        pixels[x, y] = (0, 0, 0, 0)
        elif len(crop) == 2:
            x, y = crop
            if 0 <= x < head_img.width and 0 <= y < head_img.height:
                pixels[x, y] = (0, 0, 0, 0)

    # --- Extract and overlay head overlay (only if overlay is enabled) ---
    if overlay:
        overlay_img = uploaded_image.crop((40, 8, 40+8, 8+8))
        if head["resize"] == "nearest":
            overlay_img = overlay_img.resize((target_w, target_h), Image.NEAREST)
        overlay_img = overlay_img.convert("RGBA")
        overlay_pixels = overlay_img.load()
        for crop in head["crop"]:
            if len(crop) == 4:
                x1, y1, x2, y2 = crop
                for x in range(x1, x2+1):
                    for y in range(y1, y2+1):
                        if 0 <= x < overlay_img.width and 0 <= y < overlay_img.height:
                            overlay_pixels[x, y] = (0, 0, 0, 0)
            elif len(crop) == 2:
                x, y = crop
                if 0 <= x < overlay_img.width and 0 <= y < overlay_img.height:
                    overlay_pixels[x, y] = (0, 0, 0, 0)
        head_img = Image.alpha_composite(head_img, overlay_img)

    tx, ty = head["totem"][0], head["totem"][1]
    totem_img.paste(head_img, (tx, ty), head_img)

    # --- Generate left arm ---
    left_arm = mapping["left_arm"]
    sx, sy, sw, sh = left_arm["skin"]
    left_arm_img = uploaded_image.crop((sx, sy, sx+sw, sy+sh)).convert("RGBA")
    pixels = left_arm_img.load()
    for crop in left_arm["crop"]:
        if len(crop) == 4:
            x1, y1, x2, y2 = crop
            for x in range(x1, x2+1):
                for y in range(y1, y2+1):
                    if 0 <= x < left_arm_img.width and 0 <= y < left_arm_img.height:
                        pixels[x, y] = (0, 0, 0, 0)
        elif len(crop) == 2:
            x, y = crop
            if 0 <= x < left_arm_img.width and 0 <= y < left_arm_img.height:
                pixels[x, y] = (0, 0, 0, 0)
    tx, ty = left_arm["totem"][0], left_arm["totem"][1]
    totem_img.paste(left_arm_img, (tx, ty), left_arm_img)

    # --- Generate right arm ---
    right_arm = mapping["right_arm"]
    sx, sy, sw, sh = right_arm["skin"]
    right_arm_img = uploaded_image.crop((sx, sy, sx+sw, sy+sh)).convert("RGBA")
    pixels = right_arm_img.load()
    for crop in right_arm["crop"]:
        if len(crop) == 4:
            x1, y1, x2, y2 = crop
            for x in range(x1, x2+1):
                for y in range(y1, y2+1):
                    if 0 <= x < right_arm_img.width and 0 <= y < right_arm_img.height:
                        pixels[x, y] = (0, 0, 0, 0)
        elif len(crop) == 2:
            x, y = crop
            if 0 <= x < right_arm_img.width and 0 <= y < right_arm_img.height:
                pixels[x, y] = (0, 0, 0, 0)
    tx, ty = right_arm["totem"][0], right_arm["totem"][1]
    totem_img.paste(right_arm_img, (tx, ty), right_arm_img)

    # --- Generate body ---
    body = mapping["body"]
    sx, sy, sw, sh = body["skin"]
    body_img = uploaded_image.crop((sx, sy, sx+sw, sy+sh)).convert("RGBA")
    pixels = body_img.load()
    for crop in body["crop"]:
        if len(crop) == 4:
            x1, y1, x2, y2 = crop
            for x in range(x1, x2+1):
                for y in range(y1, y2+1):
                    if 0 <= x < body_img.width and 0 <= y < body_img.height:
                        pixels[x, y] = (0, 0, 0, 0)
        elif len(crop) == 2:
            x, y = crop
            if 0 <= x < body_img.width and 0 <= y < body_img.height:
                pixels[x, y] = (0, 0, 0, 0)
    tx, ty = body["totem"][0], body["totem"][1]
    totem_img.paste(body_img, (tx, ty), body_img)

    # --- Special pixel copy actions for body (fine tuning) ---
    px = totem_img.getpixel((10, 17))
    totem_img.putpixel((10, 16), px)
    for y in range(16, 27):
        px = totem_img.getpixel((12, y))
        totem_img.putpixel((11, y), px)
    for y in range(16, 27):
        px = totem_img.getpixel((19, y))
        totem_img.putpixel((20, y), px)
    px = totem_img.getpixel((21, 17))
    totem_img.putpixel((21, 16), px)

    # --- Load and overlay outline if available ---
    outline_path = Path("outline.png")
    if outline_path.exists():
        try:
            outline_img = Image.open(outline_path).convert("RGBA")
            if outline_img.size != (32, 32):
                outline_img = outline_img.resize((32, 32), Image.NEAREST)
            totem_img = Image.alpha_composite(totem_img, outline_img)
        except Exception as e:
            pass  # Ignore error, outline is optional

    return totem_img

@app.route('/generate_totem', methods=['POST'])
def generate_totem():
    # Check if a username was provided
    username = request.form.get('username', '').strip()
    if username:
        mojang_url = f"https://api.mojang.com/users/profiles/minecraft/{username}"
        mojang_response = requests.get(mojang_url)
        if mojang_response.status_code == 200:
            uuid = mojang_response.json()['id']
            skin_url = f"https://crafatar.com/skins/{uuid}"
            response = requests.get(skin_url)
            if response.status_code == 200:
                try:
                    uploaded_image = Image.open(io.BytesIO(response.content))
                    print("[DEBUG] Username: Image loaded, type:", type(uploaded_image), "Size:", getattr(uploaded_image, 'size', 'NO SIZE'))
                except Exception as e:
                    print("[DEBUG] Error loading image (username):", e)
                    raise
            else:
                print("[DEBUG] Error: Skin konnte nicht von Crafatar geladen werden.")
                return "Skin konnte nicht von Crafatar geladen werden.", 400
        else:
            print("[DEBUG] Error: Username existiert nicht")
            return "Username existiert nicht", 400
    else:
        try:
            file = request.files['skin']
            uploaded_image = Image.open(file.stream)
            print("[DEBUG] File upload: Image loaded, type:", type(uploaded_image), "Size:", getattr(uploaded_image, 'size', 'NO SIZE'))
        except Exception as e:
            print("[DEBUG] Error loading image (file):", e)
            raise
    overlay = request.form.get('overlay', 'off') == 'on'

    # --- NEW: Image processing outsourced ---
    totem_img = generate_totem_image(uploaded_image, overlay)

    # Return image as PNG
    img_io = io.BytesIO()
    totem_img.save(img_io, 'PNG')
    img_io.seek(0)
    return send_file(img_io, mimetype='image/png')

# Content of pack.mcmeta as a string constant directly in the code
PACK_MCMETA = """{
    "pack": {
        "description": "§6Have fun with your custom §bTotem§6. §4Made with §5mc-totem.com",
        "pack_format": 15,
        "supported_formats": {
          "min_inclusive": 15,
          "max_inclusive": 99
        }
    }
}"""

@app.route('/generate_java_zip', methods=['POST'])
def generate_java_zip():
    username = request.form.get('username', '').strip()
    if username:
        mojang_url = f"https://api.mojang.com/users/profiles/minecraft/{username}"
        mojang_response = requests.get(mojang_url)
        if mojang_response.status_code == 200:
            uuid = mojang_response.json()['id']
            skin_url = f"https://crafatar.com/skins/{uuid}"
            response = requests.get(skin_url)
            if response.status_code == 200:
                try:
                    uploaded_image = Image.open(io.BytesIO(response.content))
                    print("[DEBUG] Username (ZIP): Image loaded, type:", type(uploaded_image), "Size:", getattr(uploaded_image, 'size', 'NO SIZE'))
                except Exception as e:
                    print("[DEBUG] Error loading image (username, ZIP):", e)
                    raise
            else:
                print("[DEBUG] Error: Skin konnte nicht von Crafatar geladen werden (ZIP).")
                return "Skin konnte nicht von Crafatar geladen werden.", 400
        else:
            print("[DEBUG] Error: Username existiert nicht (ZIP).")
            return "Username existiert nicht", 400
    else:
        try:
            file = request.files['skin']
            uploaded_image = Image.open(file.stream)
            print("[DEBUG] File upload (ZIP): Image loaded, type:", type(uploaded_image), "Size:", getattr(uploaded_image, 'size', 'NO SIZE'))
        except Exception as e:
            print("[DEBUG] Error loading image (file, ZIP):", e)
            raise
    overlay = request.form.get('overlay', 'off') == 'on'

    # --- NEW: Image processing outsourced ---
    totem_img = generate_totem_image(uploaded_image, overlay)

    # Save image as PNG in BytesIO
    img_io = io.BytesIO()
    totem_img.save(img_io, 'PNG')
    img_io.seek(0)

    # --- Dynamically generate pack.png from totem texture (256x256) ---
    pack_img = totem_img.resize((256, 256), Image.NEAREST)
    pack_io = io.BytesIO()
    pack_img.save(pack_io, 'PNG')
    pack_io.seek(0)

    # --- Create ZIP archive ---
    zip_io = io.BytesIO()
    with zipfile.ZipFile(zip_io, mode='w', compression=zipfile.ZIP_DEFLATED) as zipf:
        # Write totem image to ZIP
        zipf.writestr('assets/minecraft/textures/item/totem_of_undying.png', img_io.read())
        # Add pack.mcmeta directly from string constant
        zipf.writestr('pack.mcmeta', PACK_MCMETA)
        # Add pack.png generated from totem texture
        zipf.writestr('pack.png', pack_io.read())
    zip_io.seek(0)
    return send_file(zip_io, mimetype='application/zip', as_attachment=True, download_name='Custom_Totem.zip')

if __name__ == '__main__':
    app.run(debug=True) 