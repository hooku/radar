import requests
from bs4 import BeautifulSoup
import os
import time
from PIL import Image, ImageDraw, ImageFont
from datetime import datetime, timedelta
import math
import argparse
import subprocess

# URL of the webpage
url = "http://www.nmc.cn/publish/radar/huadong.html"

# Directory to save images
save_dir = "downloaded_images"
os.makedirs(save_dir, exist_ok=True)

# Global parameters for image processing
CROP_SIZE = 600
RESIZE_SIZE = 450
CROP_OFFSET_X = 50
CROP_OFFSET_Y = -150
FONT_SIZE = 45

# Global parameters for WebP compression
WEBP_QUALITY = 30  # Quality setting for lossy compression (0-100)
WEBP_LOSSLESS = False  # Toggle for lossless compression

# Global ffmpeg parameters with default fps set to 3
FFMPEG_PARAMS = [
    '-r', '3',  # Set the frames per second
    '-c:v', 'libx264',
    '-crf', '35',
    '-preset', 'veryslow',
    '-pix_fmt', 'yuv420p'
]

def download_and_process_image():
    # Send a GET request to the webpage
    response = requests.get(url)
    response.raise_for_status()  # Check if the request was successful

    # Parse the HTML content
    soup = BeautifulSoup(response.content, 'html.parser')

    # Find the image tag with id "imgpath"
    img_tag = soup.find('img', id='imgpath')
    if not img_tag:
        print("Image with id 'imgpath' not found.")
        return

    # Extract the image URL and data-time attribute
    img_url = img_tag['src']
    data_time = img_tag['data-time']

    # Convert data-time to the desired filename format
    filename_time = data_time.replace('/', '').replace(' ', '_').replace(':', '')
    png_filename = f"{filename_time}.png"
    webp_filename = f"{filename_time}.webp"

    # Check if the image has already been downloaded
    if os.path.exists(os.path.join(save_dir, webp_filename)):
        print(f"{webp_filename} already exists. Skipping download.")
        return

    attempt = 0
    max_retries = 3
    png_path = os.path.join(save_dir, png_filename)
    while attempt < max_retries:
        try:
            # Download the image
            img_data = requests.get(img_url).content
            with open(png_path, 'wb') as img_file:
                img_file.write(img_data)
            print(f"Downloaded {png_filename}")
            break
        except Exception as e:
            attempt += 1
            print(f"Attempt {attempt} failed: {e}")
            if attempt < max_retries:
                time.sleep(1)  # Wait a bit before retrying
    print(f"Failed to download {png_filename} after {max_retries} attempts")

    # Open the image for processing
    with Image.open(png_path) as img:
        # Crop the image to CROP_SIZE x CROP_SIZE pixels with offsets
        width, height = img.size
        left = (width - CROP_SIZE) / 2 + CROP_OFFSET_X
        top = (height - CROP_SIZE) / 2 + CROP_OFFSET_Y
        right = (width + CROP_SIZE) / 2 + CROP_OFFSET_X
        bottom = (height + CROP_SIZE) / 2 + CROP_OFFSET_Y
        img = img.crop((left, top, right, bottom))

        # Resize the image to RESIZE_SIZE x RESIZE_SIZE pixels with the best quality
        img = img.resize((RESIZE_SIZE, RESIZE_SIZE), Image.LANCZOS)

        # Add the timestamp to the bottom middle
        draw = ImageDraw.Draw(img)
        font = ImageFont.load_default().font_variant(size=FONT_SIZE)  # Slightly larger font size
        text = data_time
        text_bbox = draw.textbbox((0, 0), text, font=font)
        text_width = text_bbox[2] - text_bbox[0]
        text_height = text_bbox[3] - text_bbox[1]

        # Calculate the position to ensure the timestamp is within the circular boundary
        radius = RESIZE_SIZE / 2
        angle = math.acos((radius - text_height - 10) / radius)
        x_offset = radius * math.sin(angle)
        position = ((RESIZE_SIZE - text_width) / 2, RESIZE_SIZE - text_height - x_offset)

        # Draw double timestamp
        draw.text((position[0] + 1, position[1] + 1), text, (128, 128, 128), font=font)  # Grey bottom layer
        draw.text(position, text, (255, 255, 255), font=font)  # White top layer

        # Save the processed image as WebP
        webp_path = os.path.join(save_dir, webp_filename)
        if WEBP_LOSSLESS:
            img.save(webp_path, 'WEBP', lossless=True)
        else:
            img.save(webp_path, 'WEBP', quality=WEBP_QUALITY)

    # Calculate and print the compression rate
    png_size = os.path.getsize(png_path)
    webp_size = os.path.getsize(webp_path)
    compression_rate = (png_size - webp_size) / png_size * 100
    print(f"Converted to {webp_filename} with a compression rate of {compression_rate:.2f}%")

    # Optionally, remove the original PNG file
    os.remove(png_path)

def delete_old_files():
    now = datetime.now()
    cutoff = now - timedelta(hours=24)

    for filename in os.listdir(save_dir):
        if filename.endswith(".webp") or filename.endswith(".mp4"):
            file_path = os.path.join(save_dir, filename)
            file_time = datetime.fromtimestamp(os.path.getmtime(file_path))
            if file_time < cutoff:
                os.remove(file_path)
                print(f"Deleted old file: {filename}")

def create_animated_mp4s():
    now = datetime.now()
    cutoff = now - timedelta(hours=24)
    hourly_files = {}

    # Collect files by hour
    for filename in os.listdir(save_dir):
        if filename.endswith(".webp"):
            file_time_str = filename.split('.')[0]
            try:
                # Include the current year for accurate comparison
                current_year = now.year
                file_time = datetime.strptime(f"{current_year}{file_time_str}", "%Y%m%d_%H%M")
            except ValueError:
                print(f"Skipping file with invalid date format: {filename}")
                continue
            if file_time >= cutoff:
                hour_key = file_time.strftime("%Y%m%d_%H")
                if hour_key not in hourly_files:
                    hourly_files[hour_key] = []
                hourly_files[hour_key].append(filename)

    # Create MP4 videos for each hour
    for hour, files in hourly_files.items():
        hour_datetime = datetime.strptime(hour, "%Y%m%d_%H")
        if hour_datetime >= now.replace(minute=0, second=0, microsecond=0):
            continue  # Skip the current hour if it's still in range

        # Remove the year from the output filename
        output_filename = f"{hour[4:]}.mp4"
        output_path = os.path.join(save_dir, output_filename)
        if os.path.exists(output_path):
            print(f"MP4 video {output_filename} already exists. Skipping creation.")
            continue

        files.sort()  # Ensure files are in chronological order
        input_list_file = os.path.join(save_dir, f"{hour}_input.txt")
        with open(input_list_file, 'w') as f:
            for file in files:
                f.write(f"file '{file}'\n")
                f.write("duration 0.25\n");

        # Run ffmpeg command to create the MP4 file
        ffmpeg_command = [
            'ffmpeg',
            '-y',
            '-f', 'concat',
            '-safe', '0',
            '-i', input_list_file
        ] + FFMPEG_PARAMS + [output_path]

        print(' '.join(ffmpeg_command))

        subprocess.run(ffmpeg_command, check=True)
        print(f"Created MP4 video: {output_filename}")

        # Remove the temporary input list file
        os.remove(input_list_file)

def main(periodic, interval):
    if periodic:
        while True:
            download_and_process_image()
            delete_old_files()
            create_animated_mp4s()
            time.sleep(interval * 60)  # Run at the specified interval in minutes
    else:
        download_and_process_image()
        delete_old_files()
        create_animated_mp4s()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download and process radar images.")
    parser.add_argument('--periodic', action='store_true', help="Run the download function periodically")
    parser.add_argument('--interval', type=int, default=5, help="Interval in minutes for periodic run (default: 5 minutes)")
    args = parser.parse_args()

    main(args.periodic, args.interval)