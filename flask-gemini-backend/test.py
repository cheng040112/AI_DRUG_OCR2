import requests

url = "http://localhost:8080/gemini/extract"
data = {"text": "Paracetamol 500mg 每日三次 飯後服用"}

response = requests.post(url, json=data)

print("Status:", response.status_code)
print("Raw Text:", response.text)  # 看實際回傳內容
try:
    print("Parsed JSON:", response.json())
except Exception as e:
    print("⚠️ JSON decode failed:", str(e))
